#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""テスト用サーバーへコンソールコマンドを送るための最小限の RCON クライアント。

Gradle 経由で起動したサーバーの標準入力は Gradle を挟むため扱いにくい。
RCON ならサーバーへ直接コマンドを送れる。
"""

import socket
import struct

# RCON のパケット種別（Source RCON Protocol）
LOGIN_TYPE = 3
COMMAND_TYPE = 2


class RconError(RuntimeError):
    """認証失敗や通信エラーの場合に送出する。"""


class RconClient:
    """1本の TCP 接続で RCON コマンドを送るクライアント。with 文で使う。"""

    def __init__(self, host: str, port: int, password: str, timeout: float = 10.0):
        self._socket = socket.create_connection((host, port), timeout=timeout)
        self._next_id = 1
        # 認証に失敗するとサーバーはリクエストIDを -1 にして返す
        if self._request(LOGIN_TYPE, password) == -1:
            self.close()
            raise RconError("RCON authentication failed")

    def command(self, command: str) -> str:
        """コマンドを実行し、サーバーの応答テキストを返す。"""
        self._send(COMMAND_TYPE, command)
        _, payload = self._receive()
        return payload

    def close(self) -> None:
        self._socket.close()

    def __enter__(self) -> "RconClient":
        return self

    def __exit__(self, *_) -> None:
        self.close()

    def _request(self, packet_type: int, payload: str) -> int:
        self._send(packet_type, payload)
        request_id, _ = self._receive()
        return request_id

    def _send(self, packet_type: int, payload: str) -> None:
        request_id = self._next_id
        self._next_id += 1
        # 本体 = リクエストID + 種別 + ペイロード + 終端の2バイト。先頭に本体の長さを付ける
        body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\0\0"
        self._socket.sendall(struct.pack("<i", len(body)) + body)

    def _receive(self) -> tuple:
        (length,) = struct.unpack("<i", self._read_exactly(4))
        body = self._read_exactly(length)
        request_id, _ = struct.unpack("<ii", body[:8])
        return request_id, body[8:-2].decode("utf-8", errors="replace")

    def _read_exactly(self, size: int) -> bytes:
        data = b""
        while len(data) < size:
            chunk = self._socket.recv(size - len(data))
            if not chunk:
                raise RconError("RCON connection closed by the server")
            data += chunk
        return data
