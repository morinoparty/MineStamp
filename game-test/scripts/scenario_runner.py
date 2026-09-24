#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""検証済みのアクション列を、起動中のサーバーとクライアントに対して順に実行する。"""

from pathlib import Path
import re
import shutil
import struct
import time

from game_processes import ClientProcess, ServerProcess, read_log
from scenario import AssertNoLog, PressKey, Screenshot, ServerCommand, TypeText, Wait, WaitForLog
from x11_input import MinecraftWindow

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class ScenarioFailure(RuntimeError):
    """シナリオの検証（ログ待ち・スクリーンショット等）に失敗した場合に送出する。"""


class ScenarioRunner:
    """アクションの種類ごとの処理を持ち、シナリオを先頭から実行する。"""

    def __init__(self, server: ServerProcess, client: ClientProcess, output_dir: Path, window_timeout: float):
        self.server = server
        self.client = client
        self.output_dir = output_dir
        self.window_timeout = window_timeout
        # ウィンドウはキー入力が必要になった時点で探す（ログ待ちの間はまだ無くてもよい）
        self._window = None

    def run(self, actions: list) -> None:
        for index, action in enumerate(actions):
            print(f"[scenario] step {index}: {action}", flush=True)
            self.client.check_alive()
            self._execute(action)

    def take_screenshot(self, name: str) -> Path:
        """F2 でスクリーンショットを撮り、出力ディレクトリへ name.png としてコピーする。"""
        before = set(self.client.screenshots_dir.glob("*.png"))
        self.window().press_key("F2")
        screenshot = self._wait_for_new_png(before, timeout=15.0)
        destination = self.output_dir / f"{name}.png"
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(screenshot, destination)
        width, height = png_size(destination)
        print(f"[scenario] saved {destination} ({width}x{height})", flush=True)
        return destination

    @property
    def has_window(self) -> bool:
        """すでに Minecraft のウィンドウを見つけているかどうか。"""
        return self._window is not None

    def window(self) -> MinecraftWindow:
        if self._window is None:
            self._window = MinecraftWindow.wait_for(self.window_timeout)
        return self._window

    def _execute(self, action) -> None:
        if isinstance(action, PressKey):
            self.window().press_key(action.key)
        elif isinstance(action, TypeText):
            self.window().type_text(action.text)
        elif isinstance(action, Wait):
            time.sleep(action.seconds)
        elif isinstance(action, WaitForLog):
            self._wait_for_log(action)
        elif isinstance(action, AssertNoLog):
            match = re.search(action.pattern, self._log(action.source), re.MULTILINE)
            if match:
                raise ScenarioFailure(f"unexpected {action.source} log line: {match.group(0)!r}")
        elif isinstance(action, ServerCommand):
            response = self.server.command(action.command)
            print(f"[scenario] server: {response.strip()}", flush=True)
        elif isinstance(action, Screenshot):
            self.take_screenshot(action.name)
        else:
            raise TypeError(f"unsupported action: {action!r}")

    def _wait_for_log(self, action: WaitForLog) -> None:
        """ログ全体を定期的に読み直し、パターンが現れるまで待つ。"""
        pattern = re.compile(action.pattern, re.MULTILINE)
        deadline = time.monotonic() + action.timeout
        while time.monotonic() < deadline:
            if pattern.search(self._log(action.source)):
                return
            self.client.check_alive()
            time.sleep(0.5)
        raise ScenarioFailure(f"{action.source} log did not match {action.pattern!r} within {action.timeout:.0f}s")

    def _log(self, source: str) -> str:
        return read_log(self.client.latest_log if source == "client" else self.server.log_path)

    def _wait_for_new_png(self, before: set, timeout: float) -> Path:
        """新しい PNG が現れ、書き込みが終わる（サイズが変わらなくなる）まで待つ。"""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            created = sorted(set(self.client.screenshots_dir.glob("*.png")) - before)
            if created:
                candidate = created[-1]
                size = candidate.stat().st_size
                time.sleep(0.5)
                if size > 0 and candidate.stat().st_size == size and _is_png(candidate):
                    return candidate
            time.sleep(0.25)
        raise ScenarioFailure("no new screenshot was written after pressing F2")


def _is_png(path: Path) -> bool:
    with path.open("rb") as file:
        return file.read(8) == PNG_SIGNATURE


def png_size(path: Path) -> tuple:
    """PNG の IHDR チャンクから幅と高さを読む。"""
    with path.open("rb") as file:
        header = file.read(24)
    if header[:8] != PNG_SIGNATURE:
        raise ScenarioFailure(f"{path} is not a PNG file")
    return struct.unpack(">II", header[16:24])
