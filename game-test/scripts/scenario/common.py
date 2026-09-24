#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""サーバー・プレイヤーのアクションで共有する型と、対象を持たない共通アクション。"""

import re
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, PositiveFloat, field_validator

NonEmptyStr = Annotated[str, Field(min_length=1)]
# Minecraft のユーザー名の規則（3〜16文字の英数字とアンダースコア）
PlayerName = Annotated[str, Field(pattern=r"^[A-Za-z0-9_]{3,16}$")]
# on にこの値を書くとサーバー向けのアクションになるため、プレイヤー名としては使えない
SERVER_TARGET = "server"


class ActionModel(BaseModel):
    """全アクション共通の設定。未知のフィールド（タイプミス）はエラーにし、生成後は変更不可にする。"""

    model_config = ConfigDict(extra="forbid", frozen=True)


class LogPatternFields(BaseModel):
    """ログを正規表現で照合するアクションの共通フィールド。"""

    pattern: NonEmptyStr

    @field_validator("pattern")
    @classmethod
    def _compile(cls, pattern: str) -> str:
        # 実行時ではなく読み込み時に正規表現の誤りを検出する
        try:
            re.compile(pattern)
        except re.error as error:
            raise ValueError(f"invalid regular expression: {error}") from error
        return pattern


class Wait(ActionModel):
    """指定秒数だけ待つ。対象（on）を持たない共通アクション。"""

    action: Literal["wait"] = "wait"
    seconds: PositiveFloat
