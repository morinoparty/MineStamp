#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""1人のプレイヤー（仮想ディスプレイ・クライアント・ウィンドウ）をまとめて扱う。"""

from pathlib import Path
import shutil
import struct
import time

from game_processes import ClientProcess, read_log
from x11_input import MinecraftWindow
from xvfb import VirtualDisplay

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class PlayerSessionError(RuntimeError):
    """スクリーンショットの取得など、プレイヤー操作の結果が期待どおりでない場合に送出する。"""


class PlayerSession:
    """プレイヤー1人分のクライアントと、その専用ディスプレイ。"""

    def __init__(self, name: str, client: ClientProcess, display: VirtualDisplay, window_timeout: float):
        self.name = name
        self.client = client
        self.display = display
        self.window_timeout = window_timeout
        # ウィンドウはキー入力が必要になった時点で探す
        self._window = None

    def start(self, server_port: int) -> None:
        """専用ディスプレイを起動し、その上でクライアントを起動してサーバーへ参加させる。"""
        display_name = self.display.start()
        print(f"[game-test] {self.name}: display {display_name}", flush=True)
        self.client.start(server_port, display_name)

    def press_key(self, keysym: str) -> None:
        self.window().press_key(keysym)

    def type_text(self, text: str) -> None:
        self.window().type_text(text)

    def chat(self, text: str) -> None:
        """T でチャット欄を開き、入力して送信する。"""
        self.press_key("t")
        # チャット欄が開く前に入力すると先頭の文字が欠けるため、少し待つ
        time.sleep(0.5)
        self.type_text(text)
        self.press_key("Return")

    def log(self) -> str:
        """クライアントのログ全体を返す。"""
        return read_log(self.client.latest_log)

    def take_screenshot(self, destination: Path) -> Path:
        """F2 でスクリーンショットを撮り、destination へコピーする。"""
        before = set(self.client.screenshots_dir.glob("*.png"))
        self.press_key("F2")
        screenshot = self._wait_for_new_png(before, timeout=15.0)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(screenshot, destination)
        width, height = png_size(destination)
        print(f"[game-test] {self.name}: saved {destination} ({width}x{height})", flush=True)
        return destination

    def window(self) -> MinecraftWindow:
        if self._window is None:
            self._window = MinecraftWindow.wait_for(self.display.name, self.window_timeout)
        return self._window

    def check_alive(self) -> None:
        self.client.check_alive()

    def stop(self) -> None:
        self.client.stop()
        self.display.stop()

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
        raise PlayerSessionError(f"{self.name}: no new screenshot was written after pressing F2")


def _is_png(path: Path) -> bool:
    with path.open("rb") as file:
        return file.read(8) == PNG_SIGNATURE


def png_size(path: Path) -> tuple:
    """PNG の IHDR チャンクから幅と高さを読む。"""
    with path.open("rb") as file:
        header = file.read(24)
    if header[:8] != PNG_SIGNATURE:
        raise PlayerSessionError(f"{path} is not a PNG file")
    return struct.unpack(">II", header[16:24])
