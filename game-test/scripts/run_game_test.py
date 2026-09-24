#!/usr/bin/env python3
#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""Paper サーバーとバニラクライアントを起動し、JSON シナリオどおりに操作するゲーム内テスト。

xvfb-run の中で実行する想定:
    xvfb-run -a -s '-screen 0 1280x720x24' uv run --project game-test game-test/scripts/run_game_test.py \
        --scenario game-test/scenarios/stamp-thinking-face.json
"""

import argparse
from pathlib import Path
import shutil
import sys
import traceback

from game_processes import ClientProcess, GameProcessError, ServerProcess
from scenario import ScenarioError, load_scenario
from scenario_runner import ScenarioRunner
from versions import VersionError, resolve_version

PROJECT_DIR = Path(__file__).resolve().parents[2]


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scenario", type=Path, required=True, help="シナリオ JSON のパス")
    parser.add_argument(
        "--minecraft-version",
        default="latest",
        help="サーバーとクライアントのバージョン（latest なら Paper の STABLE ビルドがある最新リリース）",
    )
    parser.add_argument("--username", default="MineStampTest", help="オフラインで参加するプレイヤー名")
    parser.add_argument("--work-dir", type=Path, default=PROJECT_DIR / "build" / "game-test")
    parser.add_argument(
        "--java",
        type=Path,
        default=None,
        help="クライアントを起動する java（既定は PortableMC がバージョンに合った公式ランタイムを用意する）",
    )
    parser.add_argument("--server-timeout", type=float, default=900.0, help="ビルドを含むサーバー起動の待ち時間（秒）")
    parser.add_argument("--client-timeout", type=float, default=900.0, help="クライアントのダウンロード待ち時間（秒）")
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    # シナリオの誤りはサーバーを起動する前に検出する
    try:
        actions = load_scenario(args.scenario)
    except (OSError, ScenarioError) as error:
        print(f"invalid scenario: {error}", file=sys.stderr)
        return 2

    try:
        minecraft_version = resolve_version(args.minecraft_version)
    except (OSError, VersionError) as error:
        print(f"could not resolve the Minecraft version: {error}", file=sys.stderr)
        return 2
    print(f"[game-test] Minecraft {minecraft_version}", flush=True)

    work_dir = args.work_dir.resolve()
    logs_dir = work_dir / "logs"
    # ワールドやクライアント設定は実行ごとに作り直す（ダウンロード物の tools / cache は残す）
    for disposable in ("server", "client", "logs", "screenshots"):
        shutil.rmtree(work_dir / disposable, ignore_errors=True)

    server = ServerProcess(
        project_dir=PROJECT_DIR,
        server_dir=work_dir / "server",
        log_path=logs_dir / "server.log",
        minecraft_version=minecraft_version,
    )
    client = ClientProcess(
        tools_dir=work_dir / "tools",
        cache_dir=work_dir / "cache",
        client_dir=work_dir / "client",
        log_path=logs_dir / "client.log",
        minecraft_version=minecraft_version,
        username=args.username,
        java=args.java,
    )
    runner = ScenarioRunner(server, client, work_dir / "screenshots", window_timeout=args.client_timeout)

    try:
        print("[game-test] installing the client", flush=True)
        client.install(timeout=args.client_timeout)
        print("[game-test] starting the server", flush=True)
        server.start(timeout=args.server_timeout)
        print(f"[game-test] starting the client (server port {server.port})", flush=True)
        client.start(server.port)
        runner.run(actions)
        print("[game-test] scenario passed", flush=True)
        return 0
    except Exception:  # noqa: BLE001 - どの失敗でも診断情報を残してから終了する
        traceback.print_exc()
        capture_failure_screenshot(runner)
        print(f"[game-test] scenario failed; logs are in {logs_dir}", file=sys.stderr, flush=True)
        return 1
    finally:
        client.stop()
        server.stop()


def capture_failure_screenshot(runner: ScenarioRunner) -> None:
    """失敗時の画面を残す。ウィンドウがまだ無い等で撮れなければ諦める。"""
    # ウィンドウを見つける前に失敗した場合でも、ここでは長時間ウィンドウを待たないようにする
    if not runner.has_window:
        runner.window_timeout = 10.0
    try:
        runner.client.check_alive()
        runner.take_screenshot("failure")
    except Exception as error:  # noqa: BLE001 - 診断用の撮影失敗で本来のエラーを隠さない
        print(f"[game-test] could not capture a failure screenshot: {error}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    sys.exit(main())
