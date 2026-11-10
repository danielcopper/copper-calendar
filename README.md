# copper-calendar

Configurable Android home-screen widgets that show calendar entries from Home Assistant as a week or a month, in the look of a Home Assistant dashboard.
A tap on a day, an entry or "+" opens the matching page in the Home Assistant app.

## Build and run

The tasks run through [mise](https://mise.jdx.dev/), which brings the JDK; the Android SDK goes to `~/Android/Sdk` without root.

```sh
mise run setup    # Android command-line tools, SDK packages, local.properties
mise run gate     # build, tests with coverage, Android Lint, ktlint, detekt
mise run render   # draws the sample month's week rows to app/build/render/*.png
mise run install  # builds the debug APK and installs it with adb
```

`install` needs a phone that adb is connected to: pair it once with `adb pair` (Wireless debugging on the phone), then `adb connect <address>`; adb is in `~/Android/Sdk/platform-tools`.
For now the month widget shows sample data for November 2026.
