# In-game test

This directory holds an end-to-end test for MineStamp. It starts a real Paper server and a real vanilla Minecraft client, drives the client from a JSON scenario, and saves screenshots.

## What it does

1. **Picks versions.** It reads Mojang's [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) and the [Paper API](https://fill.papermc.io/v3/projects/paper). `latest` resolves to the newest release whose latest Paper build is `STABLE`. A range such as `1.20-` resolves to every release from 1.20 onward that has a `STABLE` Paper build. On CI, each version runs as its own matrix job.
2. **Starts the server.** It runs the `runGameTestServer` Gradle task, which starts Paper with MineStamp and ProtocolLib in `build/game-test/server`. The server runs in offline mode on a flat world, with RCON enabled so the test can send console commands.
3. **Starts the client.** [PortableMC](https://github.com/theorzr/portablemc) (a pinned version, checked against its SHA-256) installs the vanilla client and Mojang's Java runtime for that version. The client joins the server with Quick Play. No Microsoft account is needed.
4. **Runs the scenario.** The steps in `scenarios/*.json` run in order. Key presses and typing go to the client window through `xdotool`.
5. **Saves the results.** Screenshots go to `build/game-test/screenshots/`. Logs go to `build/game-test/logs/`.

Screenshots are not compared with baseline images. Stamps are drawn with particles, which are not pixel-deterministic, so the screenshots are only saved as CI artifacts.

## Scenario format

A scenario is a JSON array of actions. The actions are [pydantic](https://docs.pydantic.dev/) models in `scripts/scenario.py`, with `action` as the discriminator:

| Action | Fields | Description |
| --- | --- | --- |
| `press_key` | `key` | Press and release one key. Use an X11 keysym name, such as `F5`, `t` or `Return`. `Enter` and `Esc` also work. |
| `type_text` | `text` | Type text into the client, for example after opening chat. |
| `wait` | `seconds` | Sleep. |
| `wait_for_log` | `pattern`, `source`?, `timeout`? | Wait until a regex matches the `client` (default) or `server` log. The default timeout is 60 seconds. |
| `assert_no_log` | `pattern`, `source`? | Fail if a regex matches the log. |
| `server_command` | `command` | Run a server console command through RCON. |
| `screenshot` | `name` | Press F2 and save the new screenshot as `<name>.png`. |

Unknown actions and unknown fields are rejected before anything starts, so typos fail early.

Example (`scenarios/stamp-thinking-face.json`, shortened):

```json
[
  { "action": "wait_for_log", "pattern": "\\[CHAT\\] MineStampTest joined the game", "timeout": 300 },
  { "action": "server_command", "command": "op MineStampTest" },
  { "action": "press_key", "key": "F5" },
  { "action": "press_key", "key": "F5" },
  { "action": "press_key", "key": "t" },
  { "action": "type_text", "text": "/st :thinking-face:" },
  { "action": "press_key", "key": "Return" },
  { "action": "wait", "seconds": 1 },
  { "action": "screenshot", "name": "stamp-thinking-face" }
]
```

## Running it

Run it on Linux with X11. You need Java 25, [uv](https://docs.astral.sh/uv/), `xvfb` and `xdotool`:

```sh
xvfb-run -a -s '-screen 0 1280x720x24' \
  uv run --project game-test game-test/scripts/run_game_test.py \
    --scenario game-test/scenarios/stamp-thinking-face.json
```

Options:

- `--minecraft-version` — a release such as `1.21.11`, or `latest` (the default).
- `--java` — the Java used for the client. By default, PortableMC downloads Mojang's runtime for the version.
- `--username` — the offline player name. The default is `MineStampTest`. Scenarios refer to this name.

To list the versions that a range resolves to (this is what CI uses for its matrix):

```sh
uv run --project game-test game-test/scripts/versions.py 1.20-
```

The harness needs Minecraft 1.20 or later, because it joins with Quick Play and assumes the 1.18+ world height.

To run the unit tests for the scenario tooling:

```sh
uv run --project game-test python -B -m unittest discover -s game-test/scripts -p 'test_*.py'
```

On GitHub Actions, `.github/workflows/game_test.yml` runs this on every pull request for each version in `1.20-`. You can also start it manually and pick a version range and a scenario.

Running the test starts the Minecraft server and client, and accepts the [Minecraft EULA](https://www.minecraft.net/eula). Server and client files are never uploaded as artifacts.

## Layout

| Path | Contents |
| --- | --- |
| `pyproject.toml`, `uv.lock` | Python dependencies (pydantic), managed with uv |
| `scenarios/` | Scenario files |
| `scripts/run_game_test.py` | Entry point: resolves the version, starts the server and client, and runs the scenario |
| `scripts/scenario.py` | pydantic models for scenarios (no side effects) |
| `scripts/scenario_runner.py` | Runs each action against the server and client |
| `scripts/game_processes.py` | Starts and stops the server and client processes |
| `scripts/x11_input.py` | Sends keyboard input to the client window with `xdotool` |
| `scripts/rcon.py` | Minimal RCON client |
| `scripts/versions.py` | Resolves versions and version ranges from Mojang's manifest and the Paper API |

## Acknowledgements

This test is based on the in-game test workflow of [sya-ri/ktAdvancements](https://github.com/sya-ri/ktAdvancements/blob/master/.github/workflows/game-test.yml): a real server and a vanilla client launched with PortableMC, run under Xvfb and driven with `xdotool`. Thank you!
