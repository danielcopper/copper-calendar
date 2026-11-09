# copper-calendar

An Android app with home-screen widgets that draw the family calendar like the Home Assistant dashboard's calendar page. Decisions are in [docs/decisions.md](docs/decisions.md), the design in [docs/design.md](docs/design.md), the findings behind them in [docs/research.md](docs/research.md).

## Pipeline

- **Gate**: `mise run gate` (build, unit and render tests with coverage, Android Lint, ktlint, detekt): what CI checks, less the SonarQube Cloud gate, CodeQL and the PR title. On a fresh machine `mise run setup` first.
- **Review checks**: `mise run lint`; ktlint, detekt and Android Lint run on the whole module, not on single files.
- **Worktree task**: `mise run worktree-new`
- **Agent rules**:
  - The dev machine is a Steam Deck on SteamOS with an immutable root: no `sudo`, no system packages. Tools come through mise or live under `$HOME`; the Android SDK is in `$HOME/Android/Sdk`.
  - `/tmp` is a tmpfs: scratch files go in the agent's scratchpad under `/var/tmp/claude-1000/…`, never inside the worktree.
  - Heavy runs one at a time, never in parallel: Gradle, tests, lint. Gradle uses four workers and a 2 GB heap (`gradle.properties`).
  - Run the gate once, in the foreground, with a timeout of 1800 s.
  - Absolute paths in every command (`git -C <worktree>`); the shell's working directory resets between calls.
  - Clean up only your own processes, by PID; stop the Gradle daemon with `./gradlew --stop`, never with `pkill -f`.
  - `mise run render` writes the render test's PNGs to `app/build/render/`; look at them after a change to the drawing.
  - `homelab` is read only: the dashboard and theme the app follows live there, and this repository never writes into it.
