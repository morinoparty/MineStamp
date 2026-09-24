#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""versions.py のバージョン選択ロジックを確認するテスト（ネットワークには接続しない）。"""

import unittest

from versions import MojangManifest, PaperProject, VersionError, select_latest, select_range

MANIFEST = MojangManifest.model_validate(
    {
    "versions": [
        {"id": "26.4-snapshot-1", "type": "snapshot"},
        {"id": "26.3", "type": "release"},
        {"id": "26.3-rc-3", "type": "snapshot"},
        {"id": "26.2", "type": "release"},
        {"id": "26.1.2", "type": "release"},
    ]
    }
)
PAPER_PROJECT = PaperProject.model_validate(
    {"versions": {"26.3": ["26.3", "26.3-rc-3"], "26.2": ["26.2"], "26.1": ["26.1.2"]}}
)


class SelectLatestTest(unittest.TestCase):
    def test_skips_snapshots_and_unstable_paper_builds(self):
        channels = {"26.3": "ALPHA", "26.2": "STABLE", "26.1.2": "STABLE"}
        version = select_latest(MANIFEST.release_ids(), PAPER_PROJECT.version_ids(), channels.__getitem__)
        self.assertEqual(version, "26.2")

    def test_fails_when_no_stable_build_exists(self):
        with self.assertRaises(VersionError):
            select_latest(MANIFEST.release_ids(), PAPER_PROJECT.version_ids(), lambda _: "BETA")


class SelectRangeTest(unittest.TestCase):
    def test_open_range_returns_stable_versions_oldest_first(self):
        channels = {"26.3": "ALPHA", "26.2": "STABLE", "26.1.2": "STABLE"}
        versions = select_range(
            MANIFEST.release_ids(), PAPER_PROJECT.version_ids(), channels.__getitem__, "26.1.2", None
        )
        self.assertEqual(versions, ["26.1.2", "26.2"])

    def test_rejects_unknown_or_reversed_bounds(self):
        releases, paper = MANIFEST.release_ids(), PAPER_PROJECT.version_ids()
        with self.assertRaisesRegex(VersionError, "not a Minecraft release"):
            select_range(releases, paper, lambda _: "STABLE", "26.3-rc-3", None)
        with self.assertRaisesRegex(VersionError, "newer than"):
            select_range(releases, paper, lambda _: "STABLE", "26.2", "26.1.2")


if __name__ == "__main__":
    unittest.main()
