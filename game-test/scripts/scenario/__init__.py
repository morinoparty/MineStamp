#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""ゲーム内テストのシナリオ(JSON)を pydantic のモデルとして定義する。

外部プロセスに依存しない純粋なパッケージにしておき、サーバーやクライアントを起動する前に
シナリオの誤りを検出できるようにしている。
"""

from scenario.common import Wait
from scenario.model import PlayerSpec, Scenario, ScenarioError, load_scenario, parse_scenario
from scenario.player_actions import (
    Chat,
    PlayerAction,
    PlayerAssertNoLog,
    PlayerWaitForLog,
    PressKey,
    Screenshot,
    TypeText,
)
from scenario.server_actions import ServerAction, ServerAssertNoLog, ServerCommand, ServerWaitForLog

__all__ = [
    "Chat",
    "PlayerAction",
    "PlayerAssertNoLog",
    "PlayerSpec",
    "PlayerWaitForLog",
    "PressKey",
    "Scenario",
    "ScenarioError",
    "Screenshot",
    "ServerAction",
    "ServerAssertNoLog",
    "ServerCommand",
    "ServerWaitForLog",
    "TypeText",
    "Wait",
    "load_scenario",
    "parse_scenario",
]
