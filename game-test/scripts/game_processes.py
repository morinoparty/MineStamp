#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""テスト用の Paper サーバーとバニラクライアントを外部プロセスとして起動・停止する。"""

import hashlib
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import tarfile
import time
import urllib.request

from rcon import RconClient, RconError

# ANSI のカラーコードはログ照合の邪魔になるので取り除く
ANSI_ESCAPE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")

# PortableMC はバージョンとチェックサムを固定し、改ざんされたバイナリを実行しないようにする
PORTABLEMC_VERSION = "5.0.4"
PORTABLEMC_ARCHIVE = f"portablemc-{PORTABLEMC_VERSION}-linux-x86_64-gnu.tar.gz"
PORTABLEMC_SHA256 = "b14d2dff5191dabf90414562820ffdddfb5ee1acf692729782b4691d55b7b4f8"
PORTABLEMC_URL = (
    f"https://github.com/theorzr/portablemc/releases/download/v{PORTABLEMC_VERSION}/{PORTABLEMC_ARCHIVE}"
)

# 初回起動の案内画面やポーズを抑止し、GUI の大きさを固定するクライアント設定。
# ソフトウェアレンダリングの複数クライアントとサーバーが CPU を取り合うため、FPS も制限する
CLIENT_OPTIONS = """\
maxFps:30
enableVsync:false
guiScale:2
fullscreen:false
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
pauseOnLostFocus:false
lang:en_us
renderDistance:4
soundCategory_master:0.0
"""


class GameProcessError(RuntimeError):
    """サーバー・クライアントの起動や停止に失敗した場合に送出する。"""


def read_log(path: Path) -> str:
    """ログファイル全体を読み、ANSI エスケープを除いた文字列を返す。未作成なら空文字列。"""
    try:
        return ANSI_ESCAPE.sub("", path.read_text(encoding="utf-8", errors="replace"))
    except FileNotFoundError:
        return ""


def free_port() -> int:
    """ループバック上で空いている TCP ポートを1つ返す。"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def start_process(command: list, log_path: Path, cwd: Path, env: dict | None = None) -> subprocess.Popen:
    """コマンドを新しいプロセスグループで起動し、標準出力と標準エラーをログファイルへ書き出す。"""
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("wb") as log:
        # プロセスグループごと止められるよう start_new_session を指定する（Gradle→Java の子孫も含めるため）
        return subprocess.Popen(
            command,
            cwd=cwd,
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
            env=env,
        )


def stop_process(process: subprocess.Popen, grace_seconds: float) -> None:
    """プロセスグループに SIGTERM を送り、猶予時間内に終わらなければ SIGKILL する。"""
    if process.poll() is not None:
        return
    for sig, wait in ((signal.SIGTERM, grace_seconds), (signal.SIGKILL, 10.0)):
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            return
        try:
            process.wait(timeout=wait)
            return
        except subprocess.TimeoutExpired:
            continue


class ServerProcess:
    """run-paper の runGameTestServer タスクで起動する Paper サーバー。"""

    def __init__(self, project_dir: Path, server_dir: Path, log_path: Path, minecraft_version: str, max_players: int):
        self.project_dir = project_dir
        self.max_players = max_players
        self.server_dir = server_dir
        self.log_path = log_path
        self.minecraft_version = minecraft_version
        self.port = free_port()
        self.rcon_port = free_port()
        # RCON はループバックでのみ待ち受けるが、念のため実行ごとに使い捨てのパスワードを使う
        self.rcon_password = os.urandom(16).hex()
        self.process = None

    def start(self, timeout: float) -> None:
        """サーバー設定を書き出してから起動し、RCON で接続できるようになるまで待つ。"""
        self._write_configuration()
        command = [
            str(self.project_dir / "gradlew"),
            "runGameTestServer",
            f"-PmcVersion={self.minecraft_version}",
            "--no-daemon",
            "--console=plain",
        ]
        self.process = start_process(command, self.log_path, self.project_dir)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None:
                raise GameProcessError(f"server exited with code {self.process.returncode}; see {self.log_path}")
            # "Done (" は起動完了のログ。その後 RCON が待ち受けを開始するまで接続を試す
            if "Done (" in read_log(self.log_path):
                try:
                    self.command("list")
                    return
                except (OSError, RconError):
                    pass
            time.sleep(1.0)
        raise GameProcessError(f"server did not start within {timeout:.0f} seconds; see {self.log_path}")

    def command(self, command: str) -> str:
        """RCON でコンソールコマンドを実行し、応答を返す。"""
        with RconClient("127.0.0.1", self.rcon_port, self.rcon_password) as rcon:
            return rcon.command(command)

    def stop(self) -> None:
        """stop コマンドで正常終了させ、終わらなければプロセスごと止める。"""
        if self.process is None or self.process.poll() is not None:
            return
        try:
            self.command("stop")
            self.process.wait(timeout=60)
        except (OSError, RconError, subprocess.TimeoutExpired):
            pass
        stop_process(self.process, grace_seconds=20)

    def _write_configuration(self) -> None:
        """再現性のため毎回まっさらなディレクトリにフラットワールドの設定を書き出す。"""
        self.server_dir.mkdir(parents=True, exist_ok=True)
        (self.server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (self.server_dir / "server.properties").write_text(
            "\n".join(
                [
                    # オフラインモードのため、クライアントは Microsoft アカウント無しで参加できる
                    "online-mode=false",
                    "enforce-secure-profile=false",
                    "server-ip=127.0.0.1",
                    f"server-port={self.port}",
                    "enable-rcon=true",
                    f"rcon.port={self.rcon_port}",
                    f"rcon.password={self.rcon_password}",
                    # 平坦なワールドにして背景の地形による写り方の揺れを減らす
                    "level-type=minecraft\\:flat",
                    # 既定の "{}" だとレイヤー無しとしてエラーになるため明示する（地表は y=-61、足元は y=-60）
                    'generator-settings={"layers"\\:[{"block"\\:"minecraft\\:bedrock","height"\\:1},'
                    '{"block"\\:"minecraft\\:dirt","height"\\:2},{"block"\\:"minecraft\\:grass_block","height"\\:1}],'
                    '"biome"\\:"minecraft\\:plains"}',
                    "level-name=game-test-world",
                    "difficulty=peaceful",
                    "spawn-monsters=false",
                    "spawn-protection=0",
                    f"max-players={self.max_players}",
                    "view-distance=4",
                    "simulation-distance=4",
                    "motd=MineStamp game test",
                ]
            )
            + "\n",
            encoding="utf-8",
        )


class ClientProcess:
    """PortableMC で起動するバニラクライアント。Quick Play でテスト用サーバーへ直接参加する。"""

    def __init__(
        self,
        tools_dir: Path,
        cache_dir: Path,
        client_dir: Path,
        log_path: Path,
        minecraft_version: str,
        username: str,
        java: Path | None,
    ):
        self.tools_dir = tools_dir
        self.cache_dir = cache_dir
        self.client_dir = client_dir
        self.log_path = log_path
        self.minecraft_version = minecraft_version
        self.username = username
        self.java = java
        self.process = None

    @property
    def latest_log(self) -> Path:
        """クライアント自身のログ。チャットも [CHAT] 付きでここに出力される。"""
        return self.client_dir / "logs" / "latest.log"

    @property
    def screenshots_dir(self) -> Path:
        """F2 で撮ったスクリーンショットの保存先。"""
        return self.client_dir / "screenshots"

    def install(self, timeout: float) -> None:
        """クライアント本体とアセットを --dry でダウンロードだけしておく。"""
        self.client_dir.mkdir(parents=True, exist_ok=True)
        (self.client_dir / "options.txt").write_text(CLIENT_OPTIONS, encoding="utf-8")
        installer = start_process(self._command() + ["--dry"], self.log_path, self.tools_dir)
        try:
            code = installer.wait(timeout=timeout)
        except subprocess.TimeoutExpired as error:
            stop_process(installer, grace_seconds=5)
            raise GameProcessError(f"client installation timed out; see {self.log_path}") from error
        if code != 0:
            raise GameProcessError(f"client installation failed with code {code}; see {self.log_path}")

    def start(self, server_port: int, display: str) -> None:
        """指定ディスプレイ上でクライアントを起動し、指定ポートのサーバーへ Quick Play で参加させる。"""
        # インストール時のログを残すため、起動時のログは別ファイルに分ける
        launch_log = self.log_path.with_name(self.log_path.stem + "-launch.log")
        command = self._command() + ["--join-server", "127.0.0.1", "--join-server-port", str(server_port)]
        self.process = start_process(command, launch_log, self.tools_dir, env={**os.environ, "DISPLAY": display})

    def check_alive(self) -> None:
        """クライアントが落ちていたら例外を送出する。"""
        if self.process is not None and self.process.poll() is not None:
            raise GameProcessError(f"client exited with code {self.process.returncode}; see {self.latest_log}")

    def stop(self) -> None:
        if self.process is not None:
            stop_process(self.process, grace_seconds=10)

    def _command(self) -> list:
        return [
            str(ensure_portablemc(self.tools_dir)),
            "--output",
            "machine",
            "--main-dir",
            str(self.cache_dir),
            "start",
            self.minecraft_version,
            "--mc-dir",
            str(self.client_dir),
            # 複数クライアントを同じランナーで動かすため、ヒープは控えめにする
            "--jvm-arg=-Xms512M,-Xmx1536M",
            "--resolution",
            "1280x720",
            "--username",
            self.username,
        ] + (["--jvm", str(self.java)] if self.java else [])


def ensure_portablemc(tools_dir: Path) -> Path:
    """PortableMC をダウンロード・チェックサム検証・展開し、実行ファイルのパスを返す。"""
    executable = tools_dir / f"portablemc-{PORTABLEMC_VERSION}" / "portablemc"
    if executable.is_file():
        return executable
    tools_dir.mkdir(parents=True, exist_ok=True)
    archive = tools_dir / PORTABLEMC_ARCHIVE
    if not archive.is_file():
        urllib.request.urlretrieve(PORTABLEMC_URL, archive)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest != PORTABLEMC_SHA256:
        archive.unlink()
        raise GameProcessError(f"PortableMC checksum mismatch: {digest}")
    with tarfile.open(archive) as tar:
        # アーカイブ内のディレクトリ構成に依存しないよう、実行ファイルだけを取り出す
        member = next((m for m in tar.getmembers() if m.isfile() and Path(m.name).name == "portablemc"), None)
        if member is None:
            raise GameProcessError("portablemc executable was not found in the archive")
        source = tar.extractfile(member)
        executable.parent.mkdir(parents=True, exist_ok=True)
        executable.write_bytes(source.read())
    executable.chmod(0o755)
    return executable
