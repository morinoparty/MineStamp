#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""シナリオ全体（参加するプレイヤーとステップ列）のモデルと読み込み処理。"""

from pathlib import Path
from typing import Annotated, Any

from pydantic import BaseModel, ConfigDict, Discriminator, Field, Tag, ValidationError, model_validator

from scenario.common import SERVER_TARGET, PlayerName, Wait
from scenario.player_actions import AnyPlayerAction, PlayerAction, Screenshot
from scenario.server_actions import AnyServerAction


class ScenarioError(ValueError):
    """シナリオの形式が不正な場合に送出する。"""


class PlayerSpec(BaseModel):
    """シナリオに参加するプレイヤー。起動順に参加し、op が true なら参加後に OP にする。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    name: PlayerName
    op: bool = False


def _step_target(step: Any) -> str:
    """on の値から、ステップをどの種類のアクションとして検証するかを決める。"""
    on = step.get("on") if isinstance(step, dict) else getattr(step, "on", None)
    if on is None:
        return "common"
    return "server" if on == SERVER_TARGET else "player"


# on が無ければ共通アクション、"server" ならサーバー、それ以外はプレイヤーのアクションとして検証する
Step = Annotated[
    Annotated[Wait, Tag("common")] | Annotated[AnyServerAction, Tag("server")] | Annotated[AnyPlayerAction, Tag("player")],
    Discriminator(_step_target),
]


class Scenario(BaseModel):
    """シナリオ全体。players に宣言したプレイヤーだけが steps の on に書ける。"""

    model_config = ConfigDict(extra="forbid", frozen=True)

    players: Annotated[list[PlayerSpec], Field(min_length=1)]
    steps: Annotated[list[Step], Field(min_length=1)]

    @model_validator(mode="after")
    def _check_references(self) -> "Scenario":
        names = [player.name for player in self.players]
        if len(set(names)) != len(names):
            raise ValueError("player names must be unique")
        if SERVER_TARGET in names:
            raise ValueError(f"{SERVER_TARGET!r} is reserved and cannot be a player name")
        for index, step in enumerate(self.steps):
            if isinstance(step, PlayerAction) and step.on not in names:
                raise ValueError(f"step {index}: player {step.on!r} is not declared in players")
        # スクリーンショットはプレイヤーごとのディレクトリに保存するため、同じプレイヤー内で重複を弾く
        shots = [(step.on, step.name) for step in self.steps if isinstance(step, Screenshot)]
        duplicates = sorted({f"{on}/{name}" for on, name in shots if shots.count((on, name)) > 1})
        if duplicates:
            raise ValueError(f"duplicate screenshot names: {', '.join(duplicates)}")
        return self


def load_scenario(path: Path) -> Scenario:
    """シナリオファイルを読み込み、検証済みの Scenario を返す。"""
    try:
        return Scenario.model_validate_json(path.read_bytes())
    except ValidationError as error:
        raise ScenarioError(f"{path}: {error}") from error


def parse_scenario(data: Any) -> Scenario:
    """JSON から読み込んだ値を検証し、Scenario に変換する。"""
    try:
        return Scenario.model_validate(data)
    except ValidationError as error:
        raise ScenarioError(str(error)) from error
