#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""scenario.py の主要な分岐を確認するテスト。"""

from pathlib import Path
import unittest

from scenario import (
    PressKey,
    Screenshot,
    ScenarioError,
    ServerCommand,
    WaitForLog,
    load_scenario,
    parse_scenario,
)

SCENARIOS_DIR = Path(__file__).resolve().parents[1] / "scenarios"


class ParseScenarioTest(unittest.TestCase):
    def test_parses_actions_and_defaults(self):
        actions = parse_scenario(
            [
                {"action": "press_key", "key": "Enter"},
                {"action": "wait_for_log", "pattern": "joined"},
                {"action": "server_command", "command": "/time set noon"},
                {"action": "screenshot", "name": "stamp"},
            ]
        )
        self.assertEqual(
            actions,
            [
                PressKey("Return"),
                WaitForLog(pattern="joined", source="client", timeout=60.0),
                ServerCommand("time set noon"),
                Screenshot("stamp"),
            ],
        )

    def test_rejects_unknown_action_and_typo_fields(self):
        with self.assertRaisesRegex(ScenarioError, "unknown action"):
            parse_scenario([{"action": "press_kets", "key": "t"}])
        with self.assertRaisesRegex(ScenarioError, "unknown field"):
            parse_scenario([{"action": "press_key", "key": "t", "key_inturrupt": "t"}])

    def test_rejects_invalid_values(self):
        with self.assertRaisesRegex(ScenarioError, "positive number"):
            parse_scenario([{"action": "wait", "seconds": 0}])
        with self.assertRaisesRegex(ScenarioError, "regular expression"):
            parse_scenario([{"action": "wait_for_log", "pattern": "("}])
        with self.assertRaisesRegex(ScenarioError, "screenshot name"):
            parse_scenario([{"action": "screenshot", "name": "../escape"}])
        with self.assertRaisesRegex(ScenarioError, "duplicate"):
            parse_scenario([{"action": "screenshot", "name": "a"}, {"action": "screenshot", "name": "a"}])

    def test_bundled_scenarios_are_valid(self):
        for path in sorted(SCENARIOS_DIR.glob("*.json")):
            with self.subTest(path=path.name):
                self.assertTrue(load_scenario(path))


if __name__ == "__main__":
    unittest.main()
