#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""プレイヤー（クライアント）に対するアクション（"on": "<プレイヤー名>"）。"""

from typing import Annotated, Literal

from pydantic import Field, PositiveFloat, field_validator

from scenario.common import ActionModel, LogPatternFields, NonEmptyStr, PlayerName

# よく使われる別名を X11 の keysym 名へ寄せる（"Enter" と書いても動くようにする）
KEY_ALIASES = {
    "Enter": "Return",
    "Esc": "Escape",
    "Space": "space",
}


class PlayerAction(ActionModel):
    """プレイヤー向けアクションの基底クラス。on は scenario の players に宣言した名前。"""

    on: PlayerName


class PressKey(PlayerAction):
    """キーを1回押して離す。key は X11 の keysym 名（例: F5, t, Return）。"""

    action: Literal["press_key"] = "press_key"
    key: NonEmptyStr

    @field_validator("key")
    @classmethod
    def _resolve_alias(cls, key: str) -> str:
        return KEY_ALIASES.get(key, key)


class TypeText(PlayerAction):
    """文字列をキーボード入力する。チャット欄を開いた後などに使う。"""

    action: Literal["type_text"] = "type_text"
    text: NonEmptyStr


class Chat(PlayerAction):
    """T でチャット欄を開き、文字列を入力して Enter で送信する。コマンドも送れる。"""

    action: Literal["chat"] = "chat"
    text: NonEmptyStr


class PlayerWaitForLog(PlayerAction, LogPatternFields):
    """クライアントのログ（チャットは [CHAT] 付き）に正規表現が現れるまで待つ。"""

    action: Literal["wait_for_log"] = "wait_for_log"
    timeout: PositiveFloat = 60.0


class PlayerAssertNoLog(PlayerAction, LogPatternFields):
    """クライアントのログに正規表現が現れていないことを確認する。"""

    action: Literal["assert_no_log"] = "assert_no_log"


class Screenshot(PlayerAction):
    """バニラの F2 でスクリーンショットを撮り、<プレイヤー名>/<name>.png として保存する。"""

    action: Literal["screenshot"] = "screenshot"
    # ファイル名にそのまま使うため、パス区切り等を含められないようにする
    name: Annotated[str, Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9_.-]*$")]


# action の値で、どのプレイヤーアクションとして検証するかを決める
AnyPlayerAction = Annotated[
    PressKey | TypeText | Chat | PlayerWaitForLog | PlayerAssertNoLog | Screenshot,
    Field(discriminator="action"),
]
