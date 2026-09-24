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

import os
from pathlib import Path
import select
import shutil
import subprocess

from game_processes import GameProcessError, stop_process

SCREEN = "1280x720x24"


class VirtualDisplay:
    """1つの Xvfb サーバー。name（例: ":3"）を DISPLAY に設定して使う。"""

    def __init__(self, log_path: Path):
        self.log_path = log_path
        self.name = None
        self._process = None

    def start(self, timeout: float = 30.0) -> str:
        """空いているディスプレイ番号で Xvfb を起動し、ディスプレイ名を返す。"""
        executable = shutil.which("Xvfb")
        if executable is None:
            raise GameProcessError("Xvfb is not installed (apt-get install xvfb)")
        # -displayfd を使うと、Xvfb が空き番号を選んで準備完了後にその番号を書き込んでくれる。
        # Xvfb は最後のクライアントが切断するとリセットして番号を再度書き込もうとし、閉じたパイプへの
        # 書き込みで異常終了する（GLFW の初期化時に一度接続・切断される）。-noreset でリセットを止める
        read_fd, write_fd = os.pipe()
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            with self.log_path.open("wb") as log:
                self._process = subprocess.Popen(
                    [executable, "-displayfd", str(write_fd), "-screen", "0", SCREEN, "-nolisten", "tcp", "-noreset"],
                    stdin=subprocess.DEVNULL,
                    stdout=log,
                    stderr=subprocess.STDOUT,
                    pass_fds=(write_fd,),
                    start_new_session=True,
                )
            os.close(write_fd)
            write_fd = None
            ready, _, _ = select.select([read_fd], [], [], timeout)
            number = os.read(read_fd, 32).decode().strip() if ready else ""
        finally:
            if write_fd is not None:
                os.close(write_fd)
            os.close(read_fd)
        if not number.isdigit():
            self.stop()
            raise GameProcessError(f"Xvfb did not report a display number; see {self.log_path}")
        self.name = f":{number}"
        return self.name

    def stop(self) -> None:
        if self._process is not None:
            stop_process(self._process, grace_seconds=5)
