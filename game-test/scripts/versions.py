#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""テストに使う Minecraft バージョンを Mojang のバージョンマニフェストと Paper の API から決める。

Mojang のマニフェストでリリースの順序を取り、Paper に STABLE なビルドがあるバージョンを選ぶ。
単体で実行すると、GitHub Actions の matrix 用にバージョンの JSON 配列を出力する:
    uv run --project game-test game-test/scripts/versions.py 1.20.5-
"""

import argparse
import json
import sys
from typing import Callable
import urllib.request

from pydantic import BaseModel, ValidationError

MOJANG_VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
PAPER_PROJECT_URL = "https://fill.papermc.io/v3/projects/paper"
# Paper の API は連絡先の分かる User-Agent を求めている
USER_AGENT = "MineStamp-game-test (https://github.com/morinoparty/MineStamp)"
STABLE_CHANNEL = "STABLE"


class VersionError(RuntimeError):
    """条件に合うバージョンが見つからない場合に送出する。"""


class MojangVersion(BaseModel):
    """マニフェスト内の1バージョン。使うフィールドだけを定義し、それ以外は無視する。"""

    id: str
    type: str


class MojangManifest(BaseModel):
    """Mojang のバージョンマニフェスト。versions はリリースの新しい順に並んでいる。"""

    versions: list[MojangVersion]

    def release_ids(self) -> list[str]:
        """リリース版の ID を新しい順（マニフェストの並び順）で返す。"""
        return [version.id for version in self.versions if version.type == "release"]


class PaperProject(BaseModel):
    """Paper のプロジェクト情報。versions は {"1.21": ["1.21.11", ...]} のようにグループ化されている。"""

    versions: dict[str, list[str]]

    def version_ids(self) -> set[str]:
        """グループを平らにして、Paper にビルドがある全バージョンの集合を返す。"""
        return {version for group in self.versions.values() for version in group}


class PaperBuild(BaseModel):
    """Paper のビルド情報。チャンネル（STABLE / BETA / ALPHA）だけを使う。"""

    channel: str


def resolve_version(requested: str) -> str:
    """単一のバージョン指定（"latest" または "1.21.11" など）を1つのバージョンに解決する。"""
    versions = resolve_versions(requested)
    if len(versions) != 1:
        raise VersionError(f"{requested} must resolve to exactly one version, got {', '.join(versions)}")
    return versions[0]


def resolve_versions(spec: str) -> list[str]:
    """バージョン指定を、テストするバージョンのリスト（古い順）に解決する。

    指定できる形式:
      - "latest": Paper の STABLE ビルドがある最新リリース
      - "1.21.11": そのバージョンのみ
      - "1.20.5-": 1.20.5 以降で Paper の STABLE ビルドがある全リリース
      - "1.20.5-1.21.11": 両端を含む範囲で Paper の STABLE ビルドがある全リリース
    """
    releases = fetch_model(MOJANG_VERSION_MANIFEST_URL, MojangManifest).release_ids()
    paper_versions = fetch_model(PAPER_PROJECT_URL, PaperProject).version_ids()
    spec = spec.strip()
    if spec == "latest":
        return [select_latest(releases, paper_versions, paper_channel)]
    if "-" not in spec:
        # 明示的に1つ指定された場合は、Paper が STABLE でなくてもビルドがあれば試せるようにする
        if spec not in releases:
            raise VersionError(f"{spec} is not a Minecraft release in the Mojang version manifest")
        if spec not in paper_versions:
            raise VersionError(f"Paper has no builds for {spec}")
        return [spec]
    lower, upper = (part.strip() for part in spec.split("-", 1))
    return select_range(releases, paper_versions, paper_channel, lower, upper or None)


def select_latest(releases: list[str], paper_versions: set[str], channel_of: Callable[[str], str]) -> str:
    """新しいリリースから順に見て、Paper の最新ビルドが STABLE な最初のバージョンを返す。"""
    for version in releases:
        # 公開直後のバージョンは Paper が ALPHA/BETA のことが多いので飛ばす
        if version in paper_versions and channel_of(version) == STABLE_CHANNEL:
            return version
    raise VersionError("no Minecraft release has a stable Paper build")


def select_range(
    releases: list[str],
    paper_versions: set[str],
    channel_of: Callable[[str], str],
    lower: str,
    upper: str | None,
) -> list[str]:
    """lower から upper（None なら最新）までのリリースのうち、Paper の STABLE ビルドがあるものを古い順で返す。

    バージョン番号の大小比較は 1.21.11 と 26.1 のように体系が変わるため行わず、
    マニフェストの並び（リリースの新しい順）で範囲を決める。
    """
    for bound in (lower, upper):
        if bound is not None and bound not in releases:
            raise VersionError(f"{bound} is not a Minecraft release in the Mojang version manifest")
    newest = releases.index(upper) if upper is not None else 0
    oldest = releases.index(lower)
    if newest > oldest:
        raise VersionError(f"{lower} is newer than {upper}")
    candidates = releases[newest : oldest + 1]
    selected = [v for v in candidates if v in paper_versions and channel_of(v) == STABLE_CHANNEL]
    if not selected:
        raise VersionError(f"no release between {lower} and {upper or 'latest'} has a stable Paper build")
    return list(reversed(selected))


def paper_channel(version: str) -> str:
    """指定バージョンの Paper 最新ビルドのチャンネルを返す。"""
    return fetch_model(f"{PAPER_PROJECT_URL}/versions/{version}/builds/latest", PaperBuild).channel


def fetch_model[T: BaseModel](url: str, model: type[T]) -> T:
    """URL から JSON を取得し、指定したモデルとして検証して返す。"""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read()
    try:
        return model.model_validate_json(body)
    except ValidationError as error:
        raise VersionError(f"unexpected response from {url}: {error}") from error


def main(argv=None) -> int:
    """GitHub Actions の matrix 用に、解決したバージョンのリストを JSON で出力する。"""
    parser = argparse.ArgumentParser(description="Resolve Minecraft versions for the in-game test.")
    parser.add_argument("spec", help='"latest", "1.21.11", "1.20.5-" or "1.20.5-1.21.11"')
    args = parser.parse_args(argv)
    try:
        print(json.dumps(resolve_versions(args.spec)))
    except (OSError, VersionError) as error:
        print(f"could not resolve versions: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
