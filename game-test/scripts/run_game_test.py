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

プレイヤーごとに Xvfb を起動するため、xvfb-run は不要:
    uv run --project game-test game-test/scripts/run_game_test.py \
        --scenario game-test/scenarios/stamp-thinking-face.json
"""

import argparse
from pathlib import Path
import re
import shutil
import sys
import time
import traceback

from game_processes import ClientProcess, GameProcessError, ServerProcess, read_log
from player_session import PlayerSession
from scenario import PlayerSpec, ScenarioError, load_scenario
from scenario_runner import ScenarioRunner
from versions import VersionError, resolve_version
from xvfb import VirtualDisplay

PROJECT_DIR = Path(__file__).resolve().parents[2]


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--scenario", type=Path, required=True, help="シナリオ JSON のパス")
    parser.add_argument(
        "--minecraft-version",
        default="latest",
        help="サーバーとクライアントのバージョン（latest なら Paper の STABLE ビルドがある最新リリース）",
    )
    parser.add_argument("--work-dir", type=Path, default=PROJECT_DIR / "build" / "game-test")
    parser.add_argument(
        "--java",
        type=Path,
        default=None,
        help="クライアントを起動する java（既定は PortableMC がバージョンに合った公式ランタイムを用意する）",
    )
    parser.add_argument("--server-timeout", type=float, default=900.0, help="ビルドを含むサーバー起動の待ち時間（秒）")
    parser.add_argument("--client-timeout", type=float, default=900.0, help="クライアントのダウンロード・参加の待ち時間（秒）")
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    # シナリオの誤りはサーバーを起動する前に検出する
    try:
        scenario = load_scenario(args.scenario)
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
    for disposable in ("server", "clients", "logs", "screenshots"):
        shutil.rmtree(work_dir / disposable, ignore_errors=True)

    server = ServerProcess(
        project_dir=PROJECT_DIR,
        server_dir=work_dir / "server",
        log_path=logs_dir / "server.log",
        minecraft_version=minecraft_version,
        max_players=len(scenario.players),
    )
    players = {
        spec.name: PlayerSession(
            name=spec.name,
            client=ClientProcess(
                tools_dir=work_dir / "tools",
                # バージョンやアセットのキャッシュは全プレイヤーで共有し、設定・ログはプレイヤーごとに分ける
                cache_dir=work_dir / "cache",
                client_dir=work_dir / "clients" / spec.name,
                log_path=logs_dir / f"{spec.name}-client.log",
                minecraft_version=minecraft_version,
                username=spec.name,
                java=args.java,
            ),
            display=VirtualDisplay(logs_dir / f"{spec.name}-xvfb.log"),
            window_timeout=args.client_timeout,
        )
        for spec in scenario.players
    }
    runner = ScenarioRunner(server, players, work_dir / "screenshots")

    try:
        for session in players.values():
            print(f"[game-test] {session.name}: installing the client", flush=True)
            session.client.install(timeout=args.client_timeout)
        print("[game-test] starting the server", flush=True)
        server.start(timeout=args.server_timeout)
        for spec in scenario.players:
            join(server, players[spec.name], spec, args.client_timeout)
        runner.run(scenario.steps)
        print("[game-test] scenario passed", flush=True)
        return 0
    except Exception:  # noqa: BLE001 - どの失敗でも診断情報を残してから終了する
        traceback.print_exc()
        capture_failure_screenshots(runner)
        print(f"[game-test] scenario failed; logs are in {logs_dir}", file=sys.stderr, flush=True)
        return 1
    finally:
        for session in players.values():
            session.stop()
        server.stop()


def join(server: ServerProcess, session: PlayerSession, spec: PlayerSpec, timeout: float) -> None:
    """プレイヤーを1人ずつ参加させる。同時に起動すると CPU を取り合い、読み込みが大幅に遅くなるため。"""
    print(f"[game-test] {spec.name}: starting the client (server port {server.port})", flush=True)
    session.start(server.port)
    joined = re.compile(rf"\b{re.escape(spec.name)} joined the game")
    deadline = time.monotonic() + timeout
    while not joined.search(read_log(server.log_path)):
        if time.monotonic() > deadline:
            raise GameProcessError(f"{spec.name} did not join within {timeout:.0f} seconds")
        session.check_alive()
        time.sleep(1.0)
    if spec.op:
        print(f"[game-test] {spec.name}: {server.command(f'op {spec.name}').strip()}", flush=True)


def capture_failure_screenshots(runner: ScenarioRunner) -> None:
    """失敗時の各プレイヤーの画面を残す。ウィンドウが無い等で撮れなければ諦める。"""
    for session in runner.players.values():
        # ここでは長時間ウィンドウを待たないようにする
        session.window_timeout = 10.0
        try:
            session.check_alive()
            session.take_screenshot(runner.screenshot_path(session.name, "failure"))
        except Exception as error:  # noqa: BLE001 - 診断用の撮影失敗で本来のエラーを隠さない
            print(f"[game-test] {session.name}: could not capture a failure screenshot: {error}", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main())
