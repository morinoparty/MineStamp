#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""プレイヤーごとに独立した仮想ディスプレイ（Xvfb）を起動する。

1つのディスプレイに複数のクライアントを置くとキーボードフォーカスを奪い合うため、
クライアント1つにつきディスプレイを1つ用意する。
"""

import ctypes
from pathlib import Path
import shutil
import subprocess
import time

from game_processes import GameProcessError, stop_process

SCREEN = "1280x720x24"
# xvfb-run -a と同様に、この番号から順に空いているディスプレイ番号を探す
FIRST_DISPLAY_NUMBER = 99
MAX_ATTEMPTS = 20


class VirtualDisplay:
    """1つの Xvfb サーバー。name（例: ":99"）を DISPLAY に設定して使う。"""

    def __init__(self, log_path: Path):
        self.log_path = log_path
        self.name = None
        self._process = None

    def start(self, timeout: float = 30.0) -> str:
        """空いているディスプレイ番号で Xvfb を起動し、接続できるようになったらディスプレイ名を返す。"""
        executable = shutil.which("Xvfb")
        if executable is None:
            raise GameProcessError("Xvfb is not installed (apt-get install xvfb)")
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        number = FIRST_DISPLAY_NUMBER
        for _ in range(MAX_ATTEMPTS):
            number = _next_free_display(number)
            if self._try_start(executable, number, timeout):
                self.name = f":{number}"
                return self.name
            # 同時に別の Xvfb が同じ番号を取った等で起動できなかった場合は、次の番号で再試行する
            number += 1
        raise GameProcessError(f"could not start Xvfb after {MAX_ATTEMPTS} attempts; see {self.log_path}")

    def stop(self) -> None:
        if self._process is not None:
            stop_process(self._process, grace_seconds=5)

    def _try_start(self, executable: str, number: int, timeout: float) -> bool:
        """指定番号で Xvfb を起動し、接続できるまで待つ。起動に失敗したら False を返す。"""
        display = f":{number}"
        with self.log_path.open("ab") as log:
            # -noreset: 最後のクライアントが切断してもサーバーをリセットしない（GLFW が初期化時に一度切断するため）
            self._process = subprocess.Popen(
                [executable, display, "-screen", "0", SCREEN, "-nolisten", "tcp", "-noreset"],
                stdin=subprocess.DEVNULL,
                stdout=log,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self._process.poll() is not None:
                return False
            if _can_connect(display):
                return True
            time.sleep(0.2)
        self.stop()
        raise GameProcessError(f"Xvfb on {display} did not accept connections within {timeout:.0f} seconds")


def _next_free_display(start: int) -> int:
    """ロックファイルとソケットが無い、使われていないディスプレイ番号を返す。"""
    number = start
    while Path(f"/tmp/.X{number}-lock").exists() or Path(f"/tmp/.X11-unix/X{number}").exists():
        number += 1
    return number


def _can_connect(display: str) -> bool:
    """Xlib でディスプレイに接続できるかを確かめる。"""
    x11 = ctypes.CDLL("libX11.so.6")
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    handle = x11.XOpenDisplay(display.encode("ascii"))
    if not handle:
        return False
    x11.XCloseDisplay(handle)
    return True
