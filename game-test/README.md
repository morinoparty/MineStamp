# In-game test

MineStamp's in-game screenshot test runs on [fukurou](https://github.com/morinoparty/fukurou). fukurou starts a real Paper server and a vanilla client for each player, drives them from a JSON scenario, and saves screenshots.

This directory holds:

- `fukurou.yml` — the suite. It lists the scenario files, the shared players (Alice, an operator, and Bob), the reset settings and two fixtures: `arena` (the stone pillars both players stand on) and `front-view` (switches Alice to the front view with F5 twice).
- `scenarios/stamp-thinking-face.json` — Alice runs `/st :thinking-face:`. Alice takes a screenshot first, then Bob, who watches from across.
- `scenarios/stamp-sleeping-face.json` — Alice runs `/st :sleeping-face:`. Bob takes a screenshot first this time, then Alice, so each test leaves both players with a screenshot of the stamp actually on screen. The suite's `settle: 3` (after the harness resets the world) plus the fixture's 2-second wait comfortably clear MineStamp's 3-second stamp cooldown before this test starts.

The workflow is `.github/workflows/game_test.yml`:

1. `versions` resolves the version range (`1.21.6-` by default) with `morinoparty/fukurou/versions`.
2. `build` builds the plugin once.
3. `test` runs the whole suite on each version with `morinoparty/fukurou`, together with ProtocolLib, in a single server session.
4. `deploy` builds a viewer for all versions with `morinoparty/fukurou/ui` and uploads it to S3.
5. `preview` posts the test summary and viewer link on the pull request.

The default range starts at 1.21.6 because MineStamp is compiled to Java 25 bytecode, and Paper 1.21.4 and earlier cannot load it.

To check the suite locally:

```sh
uvx --from git+https://github.com/morinoparty/fukurou@v2 fukurou validate --suite game-test/fukurou.yml
```

See the [fukurou README](https://github.com/morinoparty/fukurou#readme) for the scenario and suite format, running it locally, and all inputs.

## Acknowledgements

This test is based on the in-game test workflow of [sya-ri/ktAdvancements](https://github.com/sya-ri/ktAdvancements/blob/master/.github/workflows/game-test.yml).
