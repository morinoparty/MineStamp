#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""Xvfb 上の Minecraft クライアントへ xdotool でキー入力を送る。

プレイヤーごとに別のディスプレイを使うため、操作対象のディスプレイ名を常に明示して渡す。
ウィンドウマネージャーの無い素の Xvfb を前提にしているため、EWMH が必要な
windowactivate ではなく XSetInputFocus 相当の windowfocus を使う。
"""

import ctypes
import os
import shutil
import subprocess
import time


class InputError(RuntimeError):
    """入力先のウィンドウが見つからない、または入力に失敗した場合に送出する。"""


# 26.x のクライアントは WM_CLASS が com.mojang.minecraft になる。古い版はタイトルで探す
WINDOW_QUERIES = (
    ("--class", r"^com\.mojang\.minecraft$"),
    ("--name", r"^Minecraft"),
)


class MinecraftWindow:
    """1つのディスプレイ上の Minecraft ウィンドウに対する入力操作をまとめたクラス。"""

    def __init__(self, display: str, window_id: int):
        self.display = display
        self.window_id = window_id

    @classmethod
    def wait_for(cls, display: str, timeout: float) -> "MinecraftWindow":
        """指定ディスプレイに Minecraft のウィンドウが表示されるまで待ち、見つかったウィンドウを返す。"""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            for option, query in WINDOW_QUERIES:
                ids = _xdotool(display, "search", "--onlyvisible", option, query, allow_no_match=True).split()
                if ids:
                    # 1つのディスプレイには1クライアントしか起動しないので、最後に作られたものを使う
                    return cls(display, int(ids[-1]))
            time.sleep(0.5)
        raise InputError(f"Minecraft window did not appear on {display} within {timeout:.0f} seconds")

    def focus(self) -> None:
        """キーボードフォーカスがこのウィンドウに無ければ移す。"""
        if self._focused_window() == self.window_id:
            return
        _xdotool(self.display, "windowraise", str(self.window_id))
        _xdotool(self.display, "windowfocus", "--sync", str(self.window_id))
        if self._focused_window() != self.window_id:
            raise InputError(f"could not give keyboard focus to the Minecraft window on {self.display}")

    def press_key(self, keysym: str) -> None:
        """キーを1回押して離す。"""
        self.focus()
        # keysym 名のまま渡すと xdotool が F5 を Alt+F5 等に解決することがあるため、
        # 修飾キーなしで届く物理キーコード（10進数）に変換して送る
        keycode = unmodified_keycode(self.display, keysym)
        _xdotool(self.display, "key", "--clearmodifiers", "--delay", "100", str(keycode))

    def type_text(self, text: str) -> None:
        """文字列をキー入力する。記号の Shift 等は xdotool が自動で付与する。"""
        self.focus()
        _xdotool(self.display, "type", "--clearmodifiers", "--delay", "50", "--", text)

    def _focused_window(self) -> int:
        return int(_xdotool(self.display, "getwindowfocus", "-f").strip())


def _xdotool(display: str, *arguments: str, allow_no_match: bool = False) -> str:
    """指定ディスプレイに対して xdotool を実行し、標準出力を返す。"""
    executable = shutil.which("xdotool")
    if executable is None:
        raise InputError("xdotool is not installed (apt-get install xdotool)")
    try:
        completed = subprocess.run(
            [executable, *arguments],
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
            env={**os.environ, "DISPLAY": display},
        )
    except subprocess.TimeoutExpired as error:
        raise InputError(f"xdotool {arguments[0]} timed out") from error
    # search は一致なしの場合に終了コード1を返すので、呼び出し側が許可した場合は正常扱いにする
    if completed.returncode != 0 and not (allow_no_match and not completed.stdout.strip()):
        raise InputError(f"xdotool {' '.join(arguments)} failed: {completed.stderr.strip()}")
    return completed.stdout


def unmodified_keycode(display: str, keysym: str) -> int:
    """keysym を、現在のキーマップで修飾キーなしに入力できるキーコードへ変換する。"""
    x11 = ctypes.CDLL("libX11.so.6")
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XStringToKeysym.argtypes = [ctypes.c_char_p]
    x11.XStringToKeysym.restype = ctypes.c_ulong
    x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
    x11.XKeysymToKeycode.restype = ctypes.c_ubyte
    x11.XkbKeycodeToKeysym.argtypes = [ctypes.c_void_p, ctypes.c_ubyte, ctypes.c_int, ctypes.c_int]
    x11.XkbKeycodeToKeysym.restype = ctypes.c_ulong
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]

    symbol = x11.XStringToKeysym(keysym.encode("ascii"))
    if not symbol:
        raise InputError(f"unknown X11 keysym: {keysym}")
    handle = x11.XOpenDisplay(display.encode("ascii"))
    if not handle:
        raise InputError(f"could not open display {display} to resolve key codes")
    try:
        keycode = x11.XKeysymToKeycode(handle, symbol)
        # シフト無しの面（group 0, level 0）で同じ keysym になるキーだけを受け付ける
        if keycode == 0 or x11.XkbKeycodeToKeysym(handle, keycode, 0, 0) != symbol:
            raise InputError(f"{keysym} cannot be typed without modifiers; use type_text instead")
        return keycode
    finally:
        x11.XCloseDisplay(handle)
