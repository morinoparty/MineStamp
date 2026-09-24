#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""検証済みのシナリオのステップを、サーバーと各プレイヤーに振り分けて順に実行する。"""

from pathlib import Path
import re
import time
from typing import Callable

from game_processes import ServerProcess, read_log
from player_session import PlayerSession
from scenario import (
    Chat,
    PlayerAction,
    PlayerAssertNoLog,
    PlayerWaitForLog,
    PressKey,
    Screenshot,
    ServerAction,
    ServerAssertNoLog,
    ServerCommand,
    ServerWaitForLog,
    TypeText,
    Wait,
)


class ScenarioFailure(RuntimeError):
    """シナリオの検証（ログ待ち・ログの確認）に失敗した場合に送出する。"""


class ScenarioRunner:
    """ステップの on を見て、サーバー・プレイヤー・共通のいずれかの処理へ振り分ける。"""

    def __init__(self, server: ServerProcess, players: dict, output_dir: Path):
        self.server = server
        # プレイヤー名 → PlayerSession
        self.players: dict[str, PlayerSession] = players
        self.output_dir = output_dir

    def run(self, steps: list) -> None:
        for index, step in enumerate(steps):
            print(f"[scenario] step {index}: {step!r}", flush=True)
            self.check_players_alive()
            if isinstance(step, ServerAction):
                self._run_server_action(step)
            elif isinstance(step, PlayerAction):
                self._run_player_action(self.players[step.on], step)
            elif isinstance(step, Wait):
                time.sleep(step.seconds)
            else:
                raise TypeError(f"unsupported step: {step!r}")

    def screenshot_path(self, player: str, name: str) -> Path:
        """スクリーンショットはプレイヤーごとのディレクトリに保存する。"""
        return self.output_dir / player / f"{name}.png"

    def check_players_alive(self) -> None:
        for session in self.players.values():
            session.check_alive()

    def _run_server_action(self, action: ServerAction) -> None:
        if isinstance(action, ServerCommand):
            response = self.server.command(action.command)
            print(f"[scenario] server: {response.strip()}", flush=True)
        elif isinstance(action, ServerWaitForLog):
            self._wait_for_log("server", lambda: read_log(self.server.log_path), action.pattern, action.timeout)
        elif isinstance(action, ServerAssertNoLog):
            _assert_no_log("server", read_log(self.server.log_path), action.pattern)
        else:
            raise TypeError(f"unsupported server action: {action!r}")

    def _run_player_action(self, session: PlayerSession, action: PlayerAction) -> None:
        if isinstance(action, PressKey):
            session.press_key(action.key)
        elif isinstance(action, TypeText):
            session.type_text(action.text)
        elif isinstance(action, Chat):
            session.chat(action.text)
        elif isinstance(action, PlayerWaitForLog):
            self._wait_for_log(session.name, session.log, action.pattern, action.timeout)
        elif isinstance(action, PlayerAssertNoLog):
            _assert_no_log(session.name, session.log(), action.pattern)
        elif isinstance(action, Screenshot):
            session.take_screenshot(self.screenshot_path(session.name, action.name))
        else:
            raise TypeError(f"unsupported player action: {action!r}")

    def _wait_for_log(self, source: str, read: Callable[[], str], pattern: str, timeout: float) -> None:
        """ログ全体を定期的に読み直し、パターンが現れるまで待つ。"""
        compiled = re.compile(pattern, re.MULTILINE)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if compiled.search(read()):
                return
            self.check_players_alive()
            time.sleep(0.5)
        raise ScenarioFailure(f"{source} log did not match {pattern!r} within {timeout:.0f}s")


def _assert_no_log(source: str, log: str, pattern: str) -> None:
    match = re.search(pattern, log, re.MULTILINE)
    if match:
        raise ScenarioFailure(f"unexpected {source} log line: {match.group(0)!r}")
