#
# Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
#
# To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
#
# You should have received a copy of the CC0 Public Domain Dedication along with this software.
# If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
"""scenario パッケージの主要な分岐を確認するテスト。"""

from pathlib import Path
import unittest

from scenario import (
    Chat,
    PlayerSpec,
    PressKey,
    ScenarioError,
    Screenshot,
    ServerCommand,
    ServerWaitForLog,
    Wait,
    load_scenario,
    parse_scenario,
)

SCENARIOS_DIR = Path(__file__).resolve().parents[1] / "scenarios"


def scenario(*steps, players=({"name": "Alice"},)):
    """テスト用に、プレイヤー定義とステップからシナリオの JSON 相当の値を作る。"""
    return {"players": list(players), "steps": list(steps)}


class ParseScenarioTest(unittest.TestCase):
    def test_routes_steps_by_target(self):
        parsed = parse_scenario(
            scenario(
                {"on": "server", "action": "command", "command": "/time set noon"},
                {"on": "server", "action": "wait_for_log", "pattern": "Done"},
                {"on": "Alice", "action": "press_key", "key": "Enter"},
                {"on": "Bob", "action": "chat", "text": "/st :thinking-face:"},
                {"on": "Bob", "action": "screenshot", "name": "stamp"},
                {"action": "wait", "seconds": 1},
                players=({"name": "Alice", "op": True}, {"name": "Bob"}),
            )
        )
        self.assertEqual(parsed.players, [PlayerSpec(name="Alice", op=True), PlayerSpec(name="Bob")])
        self.assertEqual(
            parsed.steps,
            [
                ServerCommand(command="time set noon"),
                ServerWaitForLog(pattern="Done", timeout=60.0),
                PressKey(on="Alice", key="Return"),
                Chat(on="Bob", text="/st :thinking-face:"),
                Screenshot(on="Bob", name="stamp"),
                Wait(seconds=1),
            ],
        )

    def test_rejects_actions_for_the_wrong_target_and_typos(self):
        with self.assertRaisesRegex(ScenarioError, "does not match any of the expected tags"):
            parse_scenario(scenario({"on": "server", "action": "press_key", "key": "t"}))
        with self.assertRaisesRegex(ScenarioError, "does not match any of the expected tags"):
            parse_scenario(scenario({"on": "Alice", "action": "command", "command": "stop"}))
        with self.assertRaisesRegex(ScenarioError, "Extra inputs are not permitted"):
            parse_scenario(scenario({"on": "Alice", "action": "press_key", "key": "t", "key_inturrupt": "t"}))

    def test_rejects_undeclared_or_invalid_players(self):
        with self.assertRaisesRegex(ScenarioError, "not declared in players"):
            parse_scenario(scenario({"on": "Carol", "action": "chat", "text": "hi"}))
        with self.assertRaisesRegex(ScenarioError, "reserved"):
            parse_scenario(scenario({"action": "wait", "seconds": 1}, players=({"name": "server"},)))
        with self.assertRaisesRegex(ScenarioError, "unique"):
            parse_scenario(scenario({"action": "wait", "seconds": 1}, players=({"name": "Alice"}, {"name": "Alice"})))

    def test_rejects_invalid_values(self):
        with self.assertRaisesRegex(ScenarioError, "greater than 0"):
            parse_scenario(scenario({"action": "wait", "seconds": 0}))
        with self.assertRaisesRegex(ScenarioError, "invalid regular expression"):
            parse_scenario(scenario({"on": "server", "action": "wait_for_log", "pattern": "("}))
        with self.assertRaisesRegex(ScenarioError, "should match pattern"):
            parse_scenario(scenario({"on": "Alice", "action": "screenshot", "name": "../escape"}))
        with self.assertRaisesRegex(ScenarioError, "duplicate"):
            parse_scenario(
                scenario(
                    {"on": "Alice", "action": "screenshot", "name": "a"},
                    {"on": "Alice", "action": "screenshot", "name": "a"},
                )
            )

    def test_bundled_scenarios_are_valid(self):
        for path in sorted(SCENARIOS_DIR.glob("*.json")):
            with self.subTest(path=path.name):
                self.assertTrue(load_scenario(path).steps)


if __name__ == "__main__":
    unittest.main()
