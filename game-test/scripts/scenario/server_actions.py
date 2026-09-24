#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""サーバーに対するアクション（"on": "server"）。"""

from typing import Annotated, Literal

from pydantic import Field, PositiveFloat, field_validator

from scenario.common import ActionModel, LogPatternFields, NonEmptyStr


class ServerAction(ActionModel):
    """サーバー向けアクションの基底クラス。"""

    on: Literal["server"] = "server"


class ServerCommand(ServerAction):
    """RCON でサーバーのコンソールコマンドを実行する（先頭の / は不要）。"""

    action: Literal["command"] = "command"
    command: NonEmptyStr

    @field_validator("command")
    @classmethod
    def _strip_slash(cls, command: str) -> str:
        return command.removeprefix("/")


class ServerWaitForLog(ServerAction, LogPatternFields):
    """サーバーのログに正規表現が現れるまで待つ。タイムアウトしたらテスト失敗にする。"""

    action: Literal["wait_for_log"] = "wait_for_log"
    timeout: PositiveFloat = 60.0


class ServerAssertNoLog(ServerAction, LogPatternFields):
    """サーバーのログに正規表現が現れていないことを確認する。"""

    action: Literal["assert_no_log"] = "assert_no_log"


# action の値で、どのサーバーアクションとして検証するかを決める
AnyServerAction = Annotated[
    ServerCommand | ServerWaitForLog | ServerAssertNoLog,
    Field(discriminator="action"),
]
