# In-game test

MineStamp's in-game screenshot test runs on [fukurou](https://github.com/morinoparty/fukurou). fukurou starts a real Paper server and a vanilla client for each player, drives them from a JSON scenario, and saves screenshots.

This directory only holds MineStamp's scenarios:

- `scenarios/stamp-thinking-face.json` — Alice switches to the front view (F5 twice) and runs `/st :thinking-face:`. Bob watches from across. Both take a screenshot. After the stamp cooldown, Alice sends `/st :sleeping-face:` and both take another screenshot.

The workflow is `.github/workflows/game_test.yml`:

1. `versions` resolves the version range (`1.21.6-` by default) with `morinoparty/fukurou/versions`.
2. `build` builds the plugin once.
3. `test` runs the scenario on each version with `morinoparty/fukurou`, together with ProtocolLib.
4. `deploy` builds a viewer for all versions with `morinoparty/fukurou/ui` and uploads it to S3.
5. `preview` posts the viewer link on the pull request.

The default range starts at 1.21.6 because MineStamp is compiled to Java 25 bytecode, and Paper 1.21.4 and earlier cannot load it.

To check a scenario locally:

```sh
uvx --from git+https://github.com/morinoparty/fukurou fukurou validate --scenario-file game-test/scenarios/stamp-thinking-face.json
```

See the [fukurou README](https://github.com/morinoparty/fukurou#readme) for the scenario format, running it locally, and all inputs.

## Acknowledgements

This test is based on the in-game test workflow of [sya-ri/ktAdvancements](https://github.com/sya-ri/ktAdvancements/blob/master/.github/workflows/game-test.yml).
