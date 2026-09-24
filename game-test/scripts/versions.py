#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""テストに使う Minecraft バージョンを Mojang のバージョンマニフェストと Paper の API から決める。

Mojang のマニフェストでリリースの新しい順を取り、Paper に STABLE なビルドがある最初のバージョンを選ぶ。
"""

import json
from typing import Callable
import urllib.request

MOJANG_VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
PAPER_PROJECT_URL = "https://fill.papermc.io/v3/projects/paper"
# Paper の API は連絡先の分かる User-Agent を求めている
USER_AGENT = "MineStamp-game-test (https://github.com/morinoparty/MineStamp)"
STABLE_CHANNEL = "STABLE"


class VersionError(RuntimeError):
    """条件に合うバージョンが見つからない場合に送出する。"""


def resolve_version(requested: str) -> str:
    """"latest" なら Paper の STABLE ビルドがある最新リリースを、それ以外は検証した上でそのまま返す。"""
    releases = release_ids(fetch_json(MOJANG_VERSION_MANIFEST_URL))
    paper_versions = paper_version_ids(fetch_json(PAPER_PROJECT_URL))
    if requested == "latest":
        return select_latest(releases, paper_versions, paper_channel)
    if requested not in releases:
        raise VersionError(f"{requested} is not a Minecraft release in the Mojang version manifest")
    if requested not in paper_versions:
        raise VersionError(f"Paper has no builds for {requested}")
    return requested


def release_ids(manifest: dict) -> list:
    """マニフェストからリリース版の ID を新しい順（マニフェストの並び順）で返す。"""
    return [version["id"] for version in manifest["versions"] if version["type"] == "release"]


def paper_version_ids(project: dict) -> set:
    """Paper のプロジェクト情報（"1.21": ["1.21.11", ...] のようなグループ）を平らな集合にする。"""
    return {version for group in project["versions"].values() for version in group}


def select_latest(releases: list, paper_versions: set, channel_of: Callable[[str], str]) -> str:
    """新しいリリースから順に見て、Paper の最新ビルドが STABLE な最初のバージョンを返す。"""
    for version in releases:
        # 公開直後のバージョンは Paper が ALPHA/BETA のことが多いので飛ばす
        if version in paper_versions and channel_of(version) == STABLE_CHANNEL:
            return version
    raise VersionError("no Minecraft release has a stable Paper build")


def paper_channel(version: str) -> str:
    """指定バージョンの Paper 最新ビルドのチャンネル（STABLE / BETA / ALPHA）を返す。"""
    return fetch_json(f"{PAPER_PROJECT_URL}/versions/{version}/builds/latest")["channel"]


def fetch_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)
