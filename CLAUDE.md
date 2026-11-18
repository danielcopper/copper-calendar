# copper-calendar

An Android app with home-screen widgets that draw the family calendar like the Home Assistant dashboard's calendar page. Decisions are in [docs/decisions.md](docs/decisions.md), the design in [docs/design.md](docs/design.md), the findings behind them in [docs/research.md](docs/research.md).

## Pipeline

- **Gate**: `mise run gate` (build, unit and render tests with coverage, Android Lint, ktlint, detekt): what CI checks, less the SonarQube Cloud gate, CodeQL, the check of the lock files and checksums, and the PR title. On a fresh machine `mise run setup` first.
- **Review checks**: `mise run lint`; ktlint, detekt and Android Lint run on the whole module, not on single files.
- **Dependency change**: `mise run deps-refresh` rewrites the lock files (`*.lockfile`) and `gradle/verification-metadata.xml`, which every build checks; commit them with the change after reading the diff.
- **Worktree task**: `mise run worktree-new`
- **Agent rules**:
  - Install no system packages; `mise run setup` brings what the build needs.
  - Scratch files go in the agent's scratchpad, never inside the worktree.
  - Heavy runs one at a time, never in parallel: Gradle, tests, lint. A full gate adds a few gigabytes of memory use; the daemon's workers and heap are capped in `gradle.properties`, the test JVM's heap in `app/build.gradle.kts`.
  - Run the gate once; it takes about 2 minutes.
  - Absolute paths in every command (`git -C <worktree>`); the shell's working directory resets between calls.
  - Clean up only your own processes, by PID; stop the Gradle daemon with `./gradlew --stop`, never with `pkill -f`.
  - `mise run render` writes the render tests' PNGs to `app/build/render/`; look at them after a change to the drawing, then write the widget picker's preview anew with `mise run preview` and commit it.
  - `homelab` is read only: the dashboard and theme the app follows live there, and this repository never writes into it.
