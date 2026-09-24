# In-game test

This directory holds an end-to-end test for MineStamp. It starts a real Paper server and one or more real vanilla Minecraft clients, drives them from a JSON scenario, and saves screenshots.

## What it does

1. **Picks versions.** It reads Mojang's [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) and the [Paper API](https://fill.papermc.io/v3/projects/paper). `latest` resolves to the newest release whose latest Paper build is `STABLE`. A range such as `1.20-` resolves to every release from 1.20 onward that has a `STABLE` Paper build. On CI, each version runs as its own matrix job.
2. **Starts the server.** It runs the `runGameTestServer` Gradle task, which starts Paper with MineStamp and ProtocolLib in `build/game-test/server`. The server runs in offline mode on a flat world, with RCON enabled so the test can send console commands.
3. **Starts one client per player.** Each player gets its own Xvfb display, so clients never compete for keyboard focus. [PortableMC](https://github.com/theorzr/portablemc) (a pinned version, checked against its SHA-256) installs the vanilla client and Mojang's Java runtime for that version. Players join one at a time with Quick Play, and players marked `op` are made operators. No Microsoft account is needed.
4. **Runs the scenario.** The steps run in order. Each step targets the server, one player, or neither.
5. **Saves the results.** Screenshots go to `build/game-test/screenshots/<player>/`. Logs go to `build/game-test/logs/`.

Screenshots are not compared with baseline images. Stamps are drawn with particles, which are not pixel-deterministic, so the screenshots are only saved as CI artifacts.

## Scenario format

A scenario declares its `players` and a list of `steps`. The `on` field of a step selects its target:

- `"on": "server"` runs a server action.
- `"on": "<player name>"` runs a player action on that player's client. The player must be listed in `players`.
- No `on` runs a common action.

Each group of actions is a set of [pydantic](https://docs.pydantic.dev/) models, with `action` as the discriminator. An action sent to the wrong target, an unknown field, or an undeclared player is rejected before anything starts, so typos fail early.

`players` entries:

| Field | Description |
| --- | --- |
| `name` | Offline player name (3–16 letters, digits or `_`). `server` is reserved. |
| `op` | Optional. If `true`, the player is made an operator after joining. |

Server actions (`"on": "server"`):

| Action | Fields | Description |
| --- | --- | --- |
| `command` | `command` | Run a console command through RCON. |
| `wait_for_log` | `pattern`, `timeout`? | Wait until a regex matches the server log. The default timeout is 60 seconds. |
| `assert_no_log` | `pattern` | Fail if a regex matches the server log. |

Player actions (`"on": "<player name>"`):

| Action | Fields | Description |
| --- | --- | --- |
| `press_key` | `key` | Press and release one key. Use an X11 keysym name, such as `F5`, `t` or `Return`. `Enter` and `Esc` also work. |
| `type_text` | `text` | Type text. |
| `chat` | `text` | Open chat with T, type the text, and press Enter. Works for commands too. |
| `wait_for_log` | `pattern`, `timeout`? | Wait until a regex matches the client log. Chat lines appear there with `[CHAT]`. |
| `assert_no_log` | `pattern` | Fail if a regex matches the client log. |
| `screenshot` | `name` | Press F2 and save the new screenshot as `<player>/<name>.png`. |

Common actions (no `on`):

| Action | Fields | Description |
| --- | --- | --- |
| `wait` | `seconds` | Sleep. |

Example (`scenarios/stamp-thinking-face.json`, shortened). Alice uses a stamp in front view, and Bob watches it from across:

```json
{
  "players": [
    { "name": "Alice", "op": true },
    { "name": "Bob" }
  ],
  "steps": [
    { "on": "server", "action": "command", "command": "tp Alice 0.5 -49 0.5 0 30" },
    { "on": "server", "action": "command", "command": "tp Bob 0.5 -49 8.5 180 -15" },
    { "action": "wait", "seconds": 10 },
    { "on": "Alice", "action": "press_key", "key": "F5" },
    { "on": "Alice", "action": "press_key", "key": "F5" },
    { "on": "Alice", "action": "chat", "text": "/st :thinking-face:" },
    { "action": "wait", "seconds": 1 },
    { "on": "Alice", "action": "screenshot", "name": "stamp-thinking-face" },
    { "on": "Bob", "action": "screenshot", "name": "stamp-thinking-face" }
  ]
}
```

## Running it

Run it on Linux. You need Java 25, [uv](https://docs.astral.sh/uv/), `xvfb` and `xdotool`. The script starts its own Xvfb displays, so `xvfb-run` is not needed:

```sh
uv run --project game-test game-test/scripts/run_game_test.py \
  --scenario game-test/scenarios/stamp-thinking-face.json
```

Options:

- `--minecraft-version` — a release such as `1.21.11`, or `latest` (the default).
- `--java` — the Java used for the clients. By default, PortableMC downloads Mojang's runtime for the version.

To list the versions that a range resolves to (this is what CI uses for its matrix):

```sh
uv run --project game-test game-test/scripts/versions.py 1.20-
```

The harness needs Minecraft 1.20 or later, because it joins with Quick Play and assumes the 1.18+ world height.

To run the unit tests for the scenario tooling:

```sh
uv run --project game-test python -B -m unittest discover -s game-test/scripts -p 'test_*.py'
```

On GitHub Actions, `.github/workflows/game_test.yml` runs this on every pull request for each version in `1.21.6-`. MineStamp is compiled to Java 25 bytecode, and Paper 1.21.4 and earlier cannot load it (`Unsupported class file major version 69`), so older versions are not in the default range. You can also start it manually and pick a version range and a scenario.

Running the test starts the Minecraft server and clients, and accepts the [Minecraft EULA](https://www.minecraft.net/eula). Server and client files are never uploaded as artifacts.

## Layout

| Path | Contents |
| --- | --- |
| `pyproject.toml`, `uv.lock` | Python dependencies (pydantic), managed with uv |
| `scenarios/` | Scenario files |
| `scripts/run_game_test.py` | Entry point: resolves the version, starts the server and players, and runs the scenario |
| `scripts/scenario/` | pydantic models: `server_actions.py`, `player_actions.py`, `common.py` and the whole scenario in `model.py` (no side effects) |
| `scripts/scenario_runner.py` | Sends each step to the server or to a player |
| `scripts/player_session.py` | One player: its display, client and window |
| `scripts/game_processes.py` | Starts and stops the server and client processes |
| `scripts/xvfb.py` | Starts a separate Xvfb display for each player |
| `scripts/x11_input.py` | Sends keyboard input to a client window with `xdotool` |
| `scripts/rcon.py` | Minimal RCON client |
| `scripts/versions.py` | Resolves versions and version ranges from Mojang's manifest and the Paper API |

## Acknowledgements

This test is based on the in-game test workflow of [sya-ri/ktAdvancements](https://github.com/sya-ri/ktAdvancements/blob/master/.github/workflows/game-test.yml): a real server and a vanilla client launched with PortableMC, run under Xvfb and driven with `xdotool`. Thank you!
