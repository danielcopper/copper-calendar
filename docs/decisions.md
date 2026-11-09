# Decisions

Findings are in [research.md](research.md).

## To decide

- [x] Path: configure, fork, or own widget (standalone or inside the HA app) → D1
- [x] Data source: HA, Android calendar via DAVx5, Nextcloud CalDAV directly, or a combination with a cache → D2
- [x] Sign-in: HA token and access from outside the home network → D9
- [x] Multiple people: Chantal's phone, who sees which calendars → D9, D11
- [x] Minimum Android version (the family's phones) → D12
- [x] Refresh and battery, behaviour without network → D2, D3
- [x] Distribution: signed APK sideload, own F-Droid repo, or other → D13
- [x] Tech stack, if we build our own → D7, D8
- [x] Where the public page for the HA login lives, if we use it → D10

## Decisions

- D1 (2026-11-09): We build our own small standalone Android app, not a fork of an existing calendar and not a pull request to the official Home Assistant app.
- D2 (2026-11-09): The app reads only from Home Assistant and keeps the last result in a local cache; the widgets draw from that cache. Nextcloud stays the store for events and is never contacted by the app, and Home Assistant keeps the rules.
- D4 (2026-11-09): The first version has one source, Home Assistant. The widgets depend only on a fixed entry shape that a source delivers, so another source can be added later without changing them. Rules are never evaluated in the app; a source that has no icon or person colour leaves those fields empty.
- D5 (2026-11-09): We build the two widgets and a small app to set them up (sign-in, tap targets), not a calendar app. Viewing, creating, editing and deleting stay in Home Assistant; "+" opens Home Assistant's form, so the form's suggestions are never copied. More can follow when it is needed, without changing the widgets.
- D6 (2026-11-09): The local store is a snapshot: Home Assistant's last good answer, replaced whole on each fetch. No local calendar database, no two-way sync, nothing written to the Android calendar.
- D7 (2026-11-09): Tasks run through `mise` like in the other repos (`[tools]` pins the JDK; `setup`, `build`, `test`, `lint`, `gate` wrap Gradle). The Gradle wrapper pins Gradle; the Android SDK lives under `$HOME`, installed by `setup`.
- D8 (2026-11-09): Both widgets are a vertically scrolling list of drawn rows: the month a week per row, the vertical week a day per row; invisible tap areas lie over each row, and "+" sits in a fixed layer above the list. The app is Kotlin, the setup screens Jetpack Compose. This holds once a first test on a real phone confirms scrolling, tap areas per entry and the bitmap budget; if it fails, the fallback is real Android views in the same list.
- D9 (2026-11-09): The app signs in with Home Assistant's own login (OAuth with PKCE), each person with their own account, and keeps the refresh token. It uses one address at home and away, the Nabu Casa URL; no switching between internal and external URLs.
- D10 (2026-11-09): No public page. The app's `client_id` is `http://127.0.0.1:<port>/` and its redirect `http://127.0.0.1:<port>/callback`; Home Assistant accepts a redirect on the same scheme and host as the `client_id` without fetching anything (`indieauth.py` at Core `2026.10.4`). The app listens on that port only during sign-in, uses PKCE, and keeps the exact `client_id` string, since a refresh needs the same one. Whether the login page redirects there in the phone's browser is checked on the first real sign-in; the fallback is a page in Home Assistant's `www` folder naming a custom-scheme redirect.
- D11 (2026-11-09): Each widget shows all calendars Home Assistant delivers; the setup app lets each widget leave calendars out. That is a view setting, not access control. Access control belongs to Home Assistant (planned later with Authentik and roles); the app shows whatever the signed-in account receives, so it needs no change when that comes.
- D12 (2026-11-09): Minimum Android 12 (API 31), target the current API level (37). The phones are a Pixel 10 on the latest Android and a Pixel 6, which shipped with Android 12 and is updated as far as it gets.
- D13 (2026-11-09): During development the app is installed with `adb` over Wi-Fi from the dev machine, through a `mise` task. Once it runs stably it goes to Google Play's internal testing track for the two phones, which brings updates through the Play Store and covers Google's developer verification. The package name is fixed before the first install and does not follow the repository's provisional name.
- D3 (2026-11-09): To start, the app fetches periodically every 15 minutes, the floor Android allows for periodic background work, and the sensor interval in Home Assistant stays as it is. A push from Home Assistant is added only if the delay turns out to bother us on the real phones.
