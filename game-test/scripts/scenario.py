#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""ゲーム内テストのシナリオ(JSON)を読み込み、検証済みのアクション列に変換する。

外部依存を持たない純粋なモジュールにしておき、サーバーやクライアントを起動する前に
シナリオの誤りを検出できるようにしている。
"""

from dataclasses import dataclass
import json
from pathlib import Path
import re
from typing import Union


class ScenarioError(ValueError):
    """シナリオの形式が不正な場合に送出する。"""


# よく使われる別名を X11 の keysym 名へ寄せる（"Enter" と書いても動くようにする）
KEY_ALIASES = {
    "Enter": "Return",
    "Esc": "Escape",
    "Space": "space",
}

# ログの読み取り元。client は Minecraft の logs/latest.log、server は Paper の標準出力
LOG_SOURCES = ("client", "server")

# スクリーンショット名はファイル名にそのまま使うため、パス区切り等を含められないようにする
SCREENSHOT_NAME_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]*")


@dataclass(frozen=True)
class PressKey:
    """キーを1回押して離す。key は X11 の keysym 名（例: F5, t, Return）。"""

    key: str


@dataclass(frozen=True)
class TypeText:
    """文字列をキーボード入力する。チャット欄を開いた後に使う想定。"""

    text: str


@dataclass(frozen=True)
class Wait:
    """指定秒数だけ待つ。描画の安定待ちなどに使う。"""

    seconds: float


@dataclass(frozen=True)
class WaitForLog:
    """ログに正規表現が現れるまで待つ。タイムアウトしたらテスト失敗にする。"""

    pattern: str
    source: str
    timeout: float


@dataclass(frozen=True)
class AssertNoLog:
    """ログに正規表現が現れていないことを確認する。現れていたらテスト失敗にする。"""

    pattern: str
    source: str


@dataclass(frozen=True)
class ServerCommand:
    """サーバーのコンソールにコマンドを送る（先頭の / は不要）。"""

    command: str


@dataclass(frozen=True)
class Screenshot:
    """バニラの F2 でスクリーンショットを撮り、name.png として保存する。"""

    name: str


Action = Union[PressKey, TypeText, Wait, WaitForLog, AssertNoLog, ServerCommand, Screenshot]


def load_scenario(path: Path) -> list:
    """シナリオファイルを読み込み、アクションのリストを返す。"""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ScenarioError(f"{path}: invalid JSON: {error}") from error
    return parse_scenario(data)


def parse_scenario(data) -> list:
    """JSON から読み込んだ値（アクションの配列）を検証し、アクションのリストに変換する。"""
    if not isinstance(data, list) or not data:
        raise ScenarioError("scenario must be a non-empty JSON array of actions")
    actions = [_parse_action(index, step) for index, step in enumerate(data)]
    # 同名のスクリーンショットは上書きになってしまうため、事前に弾く
    names = [action.name for action in actions if isinstance(action, Screenshot)]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        raise ScenarioError(f"duplicate screenshot names: {', '.join(duplicates)}")
    return actions


def _parse_action(index: int, step) -> Action:
    """1ステップ分の JSON オブジェクトを対応するアクションに変換する。"""
    where = f"step {index}"
    if not isinstance(step, dict):
        raise ScenarioError(f"{where}: each step must be a JSON object")
    kind = step.get("action")
    fields = {key: value for key, value in step.items() if key != "action"}

    if kind == "press_key":
        _require_fields(where, fields, {"key"})
        key = _string(where, fields, "key")
        return PressKey(KEY_ALIASES.get(key, key))
    if kind == "type_text":
        _require_fields(where, fields, {"text"})
        return TypeText(_string(where, fields, "text"))
    if kind == "wait":
        _require_fields(where, fields, {"seconds"})
        return Wait(_positive_number(where, fields, "seconds"))
    if kind == "wait_for_log":
        _require_fields(where, fields, {"pattern"}, optional={"source", "timeout"})
        return WaitForLog(
            pattern=_pattern(where, fields),
            source=_source(where, fields),
            timeout=_positive_number(where, fields, "timeout") if "timeout" in fields else 60.0,
        )
    if kind == "assert_no_log":
        _require_fields(where, fields, {"pattern"}, optional={"source"})
        return AssertNoLog(pattern=_pattern(where, fields), source=_source(where, fields))
    if kind == "server_command":
        _require_fields(where, fields, {"command"})
        return ServerCommand(_string(where, fields, "command").removeprefix("/"))
    if kind == "screenshot":
        _require_fields(where, fields, {"name"})
        name = _string(where, fields, "name")
        if not SCREENSHOT_NAME_PATTERN.fullmatch(name):
            raise ScenarioError(f"{where}: screenshot name must match {SCREENSHOT_NAME_PATTERN.pattern}")
        return Screenshot(name)
    raise ScenarioError(f"{where}: unknown action {kind!r}")


def _require_fields(where: str, fields: dict, required: set, optional: frozenset = frozenset()) -> None:
    """必須フィールドの不足と、未知のフィールド（タイプミス）を検出する。"""
    missing = required - fields.keys()
    if missing:
        raise ScenarioError(f"{where}: missing field(s): {', '.join(sorted(missing))}")
    unknown = fields.keys() - required - set(optional)
    if unknown:
        raise ScenarioError(f"{where}: unknown field(s): {', '.join(sorted(unknown))}")


def _string(where: str, fields: dict, name: str) -> str:
    value = fields[name]
    if not isinstance(value, str) or not value:
        raise ScenarioError(f"{where}: {name} must be a non-empty string")
    return value


def _positive_number(where: str, fields: dict, name: str) -> float:
    value = fields[name]
    # bool は int のサブクラスなので明示的に除外する
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value <= 0:
        raise ScenarioError(f"{where}: {name} must be a positive number")
    return float(value)


def _pattern(where: str, fields: dict) -> str:
    pattern = _string(where, fields, "pattern")
    try:
        re.compile(pattern)
    except re.error as error:
        raise ScenarioError(f"{where}: invalid regular expression: {error}") from error
    return pattern


def _source(where: str, fields: dict) -> str:
    source = fields.get("source", "client")
    if source not in LOG_SOURCES:
        raise ScenarioError(f"{where}: source must be one of {', '.join(LOG_SOURCES)}")
    return source
