#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""ゲーム内テストのシナリオ(JSON)を pydantic のモデルとして定義し、読み込む。

外部プロセスに依存しない純粋なモジュールにしておき、サーバーやクライアントを起動する前に
シナリオの誤りを検出できるようにしている。
"""

from pathlib import Path
import re
from typing import Annotated, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    PositiveFloat,
    RootModel,
    ValidationError,
    field_validator,
    model_validator,
)


class ScenarioError(ValueError):
    """シナリオの形式が不正な場合に送出する。"""


# よく使われる別名を X11 の keysym 名へ寄せる（"Enter" と書いても動くようにする）
KEY_ALIASES = {
    "Enter": "Return",
    "Esc": "Escape",
    "Space": "space",
}

NonEmptyStr = Annotated[str, Field(min_length=1)]
# ログの読み取り元。client は Minecraft の logs/latest.log、server は Paper の標準出力
LogSource = Literal["client", "server"]


class ActionModel(BaseModel):
    """全アクション共通の設定。未知のフィールド（タイプミス）はエラーにし、生成後は変更不可にする。"""

    model_config = ConfigDict(extra="forbid", frozen=True)


class PressKey(ActionModel):
    """キーを1回押して離す。key は X11 の keysym 名（例: F5, t, Return）。"""

    action: Literal["press_key"] = "press_key"
    key: NonEmptyStr

    @field_validator("key")
    @classmethod
    def _resolve_alias(cls, key: str) -> str:
        return KEY_ALIASES.get(key, key)


class TypeText(ActionModel):
    """文字列をキーボード入力する。チャット欄を開いた後に使う想定。"""

    action: Literal["type_text"] = "type_text"
    text: NonEmptyStr


class Wait(ActionModel):
    """指定秒数だけ待つ。描画の安定待ちなどに使う。"""

    action: Literal["wait"] = "wait"
    seconds: PositiveFloat


class LogPatternAction(ActionModel):
    """ログを正規表現で照合するアクションの共通部分。"""

    pattern: NonEmptyStr
    source: LogSource = "client"

    @field_validator("pattern")
    @classmethod
    def _compile(cls, pattern: str) -> str:
        # 実行時ではなく読み込み時に正規表現の誤りを検出する
        try:
            re.compile(pattern)
        except re.error as error:
            raise ValueError(f"invalid regular expression: {error}") from error
        return pattern


class WaitForLog(LogPatternAction):
    """ログに正規表現が現れるまで待つ。タイムアウトしたらテスト失敗にする。"""

    action: Literal["wait_for_log"] = "wait_for_log"
    timeout: PositiveFloat = 60.0


class AssertNoLog(LogPatternAction):
    """ログに正規表現が現れていないことを確認する。現れていたらテスト失敗にする。"""

    action: Literal["assert_no_log"] = "assert_no_log"


class ServerCommand(ActionModel):
    """サーバーのコンソールにコマンドを送る（先頭の / は不要）。"""

    action: Literal["server_command"] = "server_command"
    command: NonEmptyStr

    @field_validator("command")
    @classmethod
    def _strip_slash(cls, command: str) -> str:
        return command.removeprefix("/")


class Screenshot(ActionModel):
    """バニラの F2 でスクリーンショットを撮り、name.png として保存する。"""

    action: Literal["screenshot"] = "screenshot"
    # ファイル名にそのまま使うため、パス区切り等を含められないようにする
    name: Annotated[str, Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9_.-]*$")]


# action フィールドの値で、どのモデルとして検証するかを決める
Action = Annotated[
    PressKey | TypeText | Wait | WaitForLog | AssertNoLog | ServerCommand | Screenshot,
    Field(discriminator="action"),
]


class Scenario(RootModel[Annotated[list[Action], Field(min_length=1)]]):
    """シナリオ全体。アクションの配列そのものを JSON のルートにする。"""

    @model_validator(mode="after")
    def _unique_screenshot_names(self) -> "Scenario":
        # 同名のスクリーンショットは上書きになってしまうため、事前に弾く
        names = [action.name for action in self.root if isinstance(action, Screenshot)]
        duplicates = sorted({name for name in names if names.count(name) > 1})
        if duplicates:
            raise ValueError(f"duplicate screenshot names: {', '.join(duplicates)}")
        return self


def load_scenario(path: Path) -> list:
    """シナリオファイルを読み込み、アクションのリストを返す。"""
    try:
        return Scenario.model_validate_json(path.read_bytes()).root
    except ValidationError as error:
        raise ScenarioError(f"{path}: {error}") from error


def parse_scenario(data) -> list:
    """JSON から読み込んだ値（アクションの配列）を検証し、アクションのリストに変換する。"""
    try:
        return Scenario.model_validate(data).root
    except ValidationError as error:
        raise ScenarioError(str(error)) from error
