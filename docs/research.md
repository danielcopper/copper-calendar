# Research: family calendar widgets on Android

As of 2026-11-09. Nothing here was built, installed or tried on a phone: every statement comes from reading source code, official documentation or store listings.

Evidence tags: **S** read in source at the named version, **D** official documentation, **V** vendor claim, **U** user report or secondary source, **?** could not be verified.

## 1. What the widgets have to match

Read from `~/Repos/homelab` at `6bc81d05` (2026-11-07), `infra/homeassistant/`.

**Who owns what.** Nextcloud owns the events. Home Assistant owns everything derived from them: colour rule, kind, drawing, special days, the computed "Schule beginnt" entry. Holidays come from an HA Holiday integration and school holidays from a Remote Calendar feed, so two of the six calendars are not in Nextcloud at all. Special days exist only in HA.

**What `sensor.family_calendar` carries** (`config/packages/kalender.yaml`, macro `calendar_entries` in `config/custom_templates/zuhause.jinja`): `start`, `end`, `all_day`, `calendar`, `title`, `kind`, plus `color`, `categories`, `year`, `drawing` only where they apply. The window runs from Monday of the current week to 60 days after today. It refreshes every 15 minutes and on calendar changes.

**Gaps between the sensor and what a widget needs:**

| Gap | Where it lives today | Consequence |
|---|---|---|
| Kind to MDI icon | only `calendar_icons` in `config/dashboards/zuhause.yaml:1561` | a widget gets `kind` but not the icon |
| Colour when `color` is absent | `sensor.nextcloud_calendar_colors` and theme variables `zuhause-calendar-*` (`config/themes/`, different values for light and dark) | a widget cannot resolve the final colour from the sensor alone |
| Stable entry id | the sensor has no UID (`calendar.get_events` gives none); the page uses `/api/calendars/<entity>` for UIDs | a tap on an entry cannot name the entry yet; colours by title can differ from the page's for a changed instance of a series |
| Drawings | 21 SVGs in `config/www/zuhause/events/`, some animated | static rendering only in a widget (section 6) |
| URL parameters on the page | none: no `location.search` or `hash` in `zuhause.yaml` | opening a day or the form by link needs an HA change |
| Who sees what | `calendar_access` in `zuhause.yaml:2066`, currently `{}`, and the README says it is display only | the sensor holds every calendar for every account |

**Editing and deleting.** `main` at `6bc81d05` only created events. Branch `feature/ha-calendar-edit` (`103acad4`, 2026-11-09), in `main` by `24b4a670`, adds the services `get_event`, `find_events`, `change_event` and `delete_event`, a pencil and "Termin löschen" on an entry's sheet, series handling, and a table of what each account may do per calendar (README, "Einträge ändern und löschen"). A tap on an entry in a widget can therefore aim at that entry's sheet, which needs the entry's UID and calendar in the payload.

**The page is not the week view.** `/dashboard-zuhause/kalender` shows the day view on top, the month below and the legend at the bottom; the README says "keine Woche". The week exists as the tile "Kalender · diese Woche" on the start page (`zuhause.yaml:1860`). The week widget's model is that tile.

**Phones only know `COLOR`.** The README ("Farbe nach Person") states that DAVx5 shows a person's colour only once it is written into the event, and that only upcoming events of the family calendar get backfilled.

## 2. Comparison of existing apps and widgets

All rows: not tested on a device. "Provider" is Android's `CalendarContract`, the route DAVx5 fills.

| Candidate | Data source | Week widget | Month widget | Per-event colour / icon | Taps configurable? Arbitrary URL? | License, latest | Refresh | Tech, widget size |
|---|---|---|---|---|---|---|---|---|
| Etar v1.0.57 | Provider (S) | no (S) | no, agenda only (S) | colour yes (S), icon no | fixed; no URL (S) | GPL-3.0, 2026-07-27 | alarm at next event, `PROVIDER_CHANGED` (S) | RemoteViews, 1.3k lines |
| Etar master `82ff55ea1f` (unreleased) | Provider (S) | yes, 7 cells with titled rows (S) | yes (S) | colour yes (S), icon no, week cell fonts fixed at 7 and 8 sp | fixed; no URL (S) | GPL-3.0, merged 2026-10-26, in no release | `PROVIDER_CHANGED`, midnight alarm (S) | RemoteViews, +1.9k lines |
| Fossify Calendar 1.12.2 | Provider copied to its own DB (S) | no; requests #470, #1038 open (S) | yes (S) | colour yes (S), icon no | fixed; one option picks an internal view; no URL (S) | GPL-3.0, 2026-11-07 | `updatePeriodMillis` 1 h, 2 h wake-up alarm (S) | RemoteViews, 1.5k lines |
| Todo Agenda 4.15.1-beta | Provider and task apps (S) | no (S) | no, list only (S) | colour yes (S), icon no | fixed, implicit intents; no URL (S) | Apache-2.0, stable 4.14.1 2026-08-16 | 10 min inexact alarm, `PROVIDER_CHANGED` (S) | RemoteViews, 2.4k lines |
| Calendula v2.24.0 | Provider, no network (S) | strip of dots, no titles (S) | yes (S) | colour yes, tone-adjusted (S), icon no | fixed; no URL (S) | MIT, 2026-11-06 | `PROVIDER_CHANGED`, midnight, 15 min cap (S) | Glance plus RemoteViews, 5.4k lines |
| KashCal v2026.11.04 | own CalDAV (Nextcloud) and provider (S) | yes, a 7-day list from today, no grid (S) | yes (S) | colour yes (S), emoji from title keywords only | fixed; no URL (S) | Apache-2.0 by LICENSE text (GitHub detects none), 2026-11-05 | WorkManager 30 min, midnight alarm (S) | Glance, 13k lines |
| Business Calendar 2 | "Android calendar synchronization" (V), DAVx5 route ? | yes (V) | yes (V) | emoticons in both widgets (V), MDI no; colour per event ? | header selectable (V), day and event ?; URL undocumented | proprietary, 2026-10-20 | undocumented | closed |
| aCalendar+ | "Android's native calendar backend" (V) | yes (V) | yes (V) | in app (V), in widget undocumented | "Start app in", "on touched day" (V); URL undocumented | proprietary, 2025-09-08 | undocumented | closed |
| Calendar Widgets Suite | device calendars (V) | yes, several (V) | yes (V) | icon not mentioned | event opens "your own calendar app" (V) | proprietary, 2026-11-05, 100+ downloads | undocumented | closed |
| KWGT (widget builder) | `ci()` by index (V); web `wg()` (V) | build it yourself | build it yourself | images and SVG (V) | per-object touch, "open Url" (V); `homeassistant://` ? | proprietary, 3.82, 2026-07-30 | undocumented | closed |

No battery measurement exists for any candidate; only Home Agenda (not listed above) makes a claim, and that is marketing.

Excluded after reading: DigiCal (contradictory vendor statements on data source), One Calendar (listing mentions no widgets), Home Agenda and "Calendar Widget Month + Agenda" (no week widget listed), Simple Calendar (last release 2023, superseded by Fossify), `plusonelabs/calendar-widget` (last commit 2020).

**KWGT** is the only product where both a look close to ours and a URL tap are documented. `wg(url, json, jsonpath)` reads JSON from a URL ([docs](https://docs.kustom.rocks/docs/reference/functions/wg/)). A bearer token for HA is unconfirmed: a forum post shows headers only on a Flow action ([forum](https://forum.kustom.rocks/t/need-help-with-webget/9251), U). Event rows are addressed by index with no native list or grid ([`ci`](https://docs.kustom.rocks/tags/ci/)), so a 42-cell month is built by hand.

Sources: [Etar](https://github.com/Etar-Group/Etar-Calendar) ([PR #2042](https://github.com/Etar-Group/Etar-Calendar/pull/2042); its author notes LLM generation and no mobile background), [Fossify](https://github.com/FossifyOrg/Calendar), [Todo Agenda](https://github.com/andstatus/todoagenda), [Calendula](https://codeberg.org/jlmakiola/calendula), [KashCal](https://github.com/KashCal/KashCal), Business Calendar [widget types](https://appgenix.uservoice.com/knowledgebase/articles/900939-overview-of-widget-types) and [emoticons](https://appgenix.uservoice.com/knowledgebase/articles/1080985--can-i-add-icons-to-my-events-tasks), aCalendar+ [overview](https://tapirappsgmbh.freshdesk.com/en/support/solutions/articles/36000103621-widget-overview).

## 3. Home Assistant as a source

Read at Android app tag `2026.11.1` (2026-11-02), Core `2026.10.4`, frontend `20260930.5`.

**Companion app widgets (S).** Button, camera, entity state, media player and template use RemoteViews; only the to-do widget uses Glance 1.2.0. There is no calendar widget, merged or in a PR. The only requests are discussions [#4767](https://github.com/orgs/home-assistant/discussions/4767) and [#1738](https://github.com/orgs/home-assistant/discussions/1738), with no maintainer decision. The template widget is one centred `TextView` with basic HTML and its tap only re-renders, so it cannot carry a calendar grid.

**Deep links (S, D).** `homeassistant://navigate/<path>` is declared in the manifest on an exported activity and [documented](https://companion.home-assistant.io/docs/integrations/url-handler/). `LinkHandler.kt` keeps path, query and fragment and strips only `server` and `server_id`; a unit test covers query and fragment. A widget or third-party app fires it with `Intent(ACTION_VIEW, uri)`. The to-do widget's own tap uses `todo?entity_id=…&add_item=true`, so a query string into the frontend is existing practice. Whether a query reaches a custom card at runtime is ? (the frontend leaves unknown parameters alone in `hui-root.ts`; S, not executed).

**API (S).**

| Route | Access | Note |
|---|---|---|
| `GET /api/states/<entity>` | READ, any user | full attributes, no truncation in the view |
| `GET /api/calendars/<id>?start&end` | READ | `summary`, `start`, `end`, `uid`, `rrule` and more; no `COLOR`, no `CATEGORIES` |
| `POST /api/services/calendar/get_events?return_response` | CONTROL on the entity | no UID in the response |
| WS `render_template` | no admin required | what the page uses; REST `/api/template` is admin only |
| WS `subscribe_entities` with `entity_ids` | READ | push for one entity |
| WS `subscribe_trigger` | admin only | not usable for family accounts |

**Authentication (S, D).** The app's tokens stay in its own database and no component exposes them to other apps (manifests checked, not every component body). A separate app needs either a long-lived token (valid 10 years, any user can create one) or OAuth with PKCE: access token 30 minutes, refresh token expires after 90 days unused, `client_id` must be an http(s) URL. Remote access in the app is internal, cloud or external URL chosen by network; there is no tunnel of its own. This household reaches HA from outside through Nabu Casa (homelab README).

**Contributing to the official app.** Rules: small PRs, tests, light and dark screenshots, a docs PR ([submitting](https://contributors.home-assistant.io/companion/android/submitting/)). Precedents: to-do widget #5169 merged in 16 days with 72 review comments; graph widget #4601 closed unmerged; climate widget #7340 open since 2026-08-17. Upstream widgets bind to generic entities, none reads a named custom sensor (S). `AI_POLICY.md` closes PRs from autonomous agents and requires the contributor to explain every change and answer maintainers personally.

**Self-built official app (S, D, inference).** A build with our own key cannot update the Play install, so it means a second install or replacing it, a new login and a new device entry. With the mock Firebase file, notifications run over WebSocket only. Updates are manual rebuilds.

## 4. Data source options

| Option | Offline | Kind, icon, drawing | Colour rule | Special days, school start | Our rules stay in one place |
|---|---|---|---|---|---|
| HA | needs a cache | yes | yes | yes | yes |
| Android provider via DAVx5 | yes | no | only `COLOR`, and only where it is set and enabled in DAVx5 (S) | no | no |
| Nextcloud CalDAV directly | needs a cache | no | `COLOR` and `CATEGORIES` yes (RFC 7986), person rule no | no | no |
| HA plus provider as a fallback | yes | online only | online only | online only | yes, but two look states |

DAVx5 stores `CATEGORIES` as an extended property that any app with `READ_CALENDAR` can query (S, [`CategoriesBuilder.kt`](https://github.com/bitfireAT/synctools/blob/main/lib/src/main/kotlin/at/bitfire/synctools/mapping/calendar/builder/CategoriesBuilder.kt)), and `COLOR` only with the account option on (D, [manual](https://manual.davx5.com/technical_information.html)). The rules would still have to be rebuilt to use them. Direct CalDAV would need `dav4jvm` (MPL-2.0, 5.0.0) and recurrence expansion in the app.

### Could Nextcloud take over from HA, or HA from Nextcloud?

| What HA does today | Possible in Nextcloud? |
|---|---|
| Own and person colour | yes, as `COLOR`; forms already write it, and backfilling covers only upcoming events of the family calendar (homelab README) |
| Categories | already there (`CATEGORIES`) |
| Public and school holidays | probably as subscribed calendars ([admin manual](https://docs.nextcloud.com/server/stable/admin_manual/groupware/calendar.html), D); DAVx5 delivery ? |
| Special days, "Schule beginnt" | only if something generates a calendar for them |
| Kind from about 70 title and category rules | no rule engine found: [Flow](https://nextcloud.com/blog/how-to-automate-business-processes-with-nextcloud-flow-your-workflow-automation-assistant/) documents no action that changes an event (U, absence not proven). An external job could write the result into each event |
| Icon and drawing | presentation only; none of the widgets read shows a per-event picture from the calendar |

HA as the calendar store does not work for this household. Its event model has no colour or categories (`CalendarEvent`, Core `2026.10.4`, S), so the person rule's input would be lost. HA's `caldav` integration is a client, and no CalDAV server in Core was found, so DAVx5, phone reminders and offline access would go. Birthdays also come from the Nextcloud address book.

Nextcloud's `webhook_listeners` app offers calendar object events ([admin manual](https://docs.nextcloud.com/server/stable/admin_manual/webhook_listeners/index.html), D). A third-party test reports delete events not arriving (U). It could replace the daily 04:00 colour read of `sensor.nextcloud_calendar_colors`.

## 5. Update and battery facts

- `updatePeriodMillis` no more often than 30 minutes; WorkManager periodic minimum 15 minutes (D).
- Doze defers jobs, network and normal alarms (D). `set()` needs no permission and fires within about an hour on Android 12+; exact alarms need a user-granted permission (D, [alarms](https://developer.android.com/develop/background-work/services/alarms)).
- The official HA app subscribes to widget data only while the screen is on and stops on screen off (S). That is the existing pattern on the phones.
- Nextcloud to HA: `sensor.family_calendar` runs on `time_pattern` `minutes: "/15"` (`packages/kalender.yaml:78`), which is our own setting. Each run calls `calendar.get_events`, which reaches `calendar.search` on the server (`caldav/coordinator.py`, S, not executed). The 15 minutes in that coordinator (`MIN_TIME_BETWEEN_UPDATES`) only drive the entity's state. A shorter sensor interval also re-renders the cards and reloads the months shown on the page (homelab README).
- HA to the app: the 15-minute floor is Android's WorkManager, not HA. HA can reach a phone with the notification command [`command_broadcast_intent`](https://companion.home-assistant.io/docs/notifications/notification-commands) (`intent_package_name`, `intent_action`, `intent_extras`; D), handled in `MessagingManager.kt` (S). Whether that shows anything to the user, and how it behaves in Doze, is ?.
- Apps targeting API 37 need `ACCESS_LOCAL_NETWORK` to reach LAN addresses (D, [Android 17](https://developer.android.com/about/versions/17/behavior-changes-17)). Going through the Nabu Casa URL does not touch the LAN.

## 6. Technology facts for a custom build

**Language.** A home-screen widget is native on every route. Flutter's `home_widget` states it does "not allow writing Widgets with Flutter itself" (D). `react-native-android-widget` renders to an image and needs the exact size (D). A PWA has no Android widget route; `WebView` is not an allowed `RemoteViews` class (D). Kotlin Multiplatform leaves the widget as Android code (D).

| Rendering approach | For | Against |
|---|---|---|
| Jetpack Glance 1.2.0 (2026-08-26) | Compose style, used by HA's to-do widget and KashCal | `Row` truncated to 10 children and `LazyVerticalGrid` limited to 1–5 fixed columns (S, 7 columns need 7 children in a `Row`); no custom fonts (D); Calendula hit `TransactionTooLargeException` on its month and fell back to embedded RemoteViews (S) |
| RemoteViews with XML | full control of cells, per-cell `PendingIntent`, as Etar and Fossify do | verbose, no custom fonts; icons as vector drawables |
| Canvas bitmap of the grid plus tap overlay | closest to the dashboard: any font, MDI glyphs, drawn SVGs, one image per size ([Persian Calendar](https://github.com/persian-calendar/persian-calendar) `UpdateUtils.kt`, S) | invisible to screen readers; re-render per theme and size; bitmap budget of `6 × width × height` bytes on Android 17 (D), about 15 MB at 1080×2400 |

**Scrolling and swiping.** A widget can only scroll through collection views: `ListView`, `GridView` (vertical) and `StackView`; there is no `ScrollView` or `HorizontalScrollView` among the allowed `RemoteViews` classes (D, [RemoteViews](https://developer.android.com/reference/android/widget/RemoteViews)). Horizontal swipes belong to the launcher. Snap scrolling arrives only with Remote Compose (alpha, Android 17, D). So a week that scrolls sideways is not possible today; a list of rows that scrolls vertically is, and each row can be a drawn image. A fixed "+" sits in a layer above the list.

**Sign-in with the HA login.** HA's OAuth accepts a native app's own scheme as redirect (for example `app:/callback`) only when the `client_id` page, an https URL, advertises it (`indieauth.py:65-68`, `:163-176` at Core `2026.10.4`, S). `homeassistant://auth-callback` is reserved for the official apps (`:54-59`, S). A redirect on the same scheme and host as the `client_id` is accepted without any fetch (`:41-47`, S), and a `client_id` may be a loopback or other local address (`_parse_client_id`, `:384-418`, S). So a native app can use `http://127.0.0.1:<port>/` as `client_id` and a redirect on the same address (the loopback pattern of RFC 8252) without any public page; not tried in a browser.

**Local preview.** Robolectric 4.17 (2026-09-10) and Paparazzi (2.0 only as alpha, 2026-09-28) render Android drawing on the JVM to images, which would allow looking at widget renders on the Deck without a phone; not tried.

**Icons.** MDI 7.4.47 is Apache-2.0 ([license](https://pictogrammers.com/docs/general/license/)). Only the icons in `calendar_icons` are needed, so a small vector set or a subset is enough. HA's own button widget draws MDI to bitmaps (S). AndroidSVG 1.4 renders static SVG and has no SMIL or CSS animation (D), so drawings are still images in a widget. RemoteViews has no general animation API (D).

**Toolchain on this machine.** Android SDK in `$HOME` needs no root (D, inference); AGP 9.5 needs JDK 17 and Gradle 9.6 (D). `/dev/kvm` exists and is world-writable here, measured today; whether the emulator runs on SteamOS is ?. GitHub Actions Ubuntu 24.04 images carry the SDK up to API 37 (D). Wireless `adb pair` works on Android 11+ (D).

**Distribution.**

- Today a directly sideloaded APK has no verification requirement; enforcement since 2026-09-30 covers named stores in four countries (D, [FAQ](https://developer.android.com/developer-verification/guides/faq)).
- Global enforcement on certified devices is announced for 2027, no month (D).
- After that: ADB installs stay free of registration. A free limited-distribution account covers family use without ID, capped at 20 devices per the FAQ and 50 per the [limited-distribution guide](https://developer.android.com/developer-verification/guides/limited-distribution), a conflict in Google's own pages. Unregistered apps need the one-time advanced flow, with a 24-hour wait, and it has to stay enabled for updates (D).
- Own F-Droid repo and Obtainium are possible channels (D); both sit in the same position under verification (U).

## 7. The three paths

Effort figures are rough estimates of active work, not measurements.

### (a) Configure an existing app or widget

- **Effort:** hours with Business Calendar 2 or aCalendar+. Days to weeks with KWGT.
- **Result:** Week and month exist, but no per-entry icons from our kinds, no drawings, no special days, no person colour beyond `COLOR`, no documented tap into HA.
- **KWGT** might reach HA; unknowns are bearer auth, refresh and a hand-built 42-cell layout.
- **Risk:** low to try, high that it misses the goal. It cannot meet "looks like HA and lands in HA".

### (b) Fork an open-source widget

- **Best base:** KashCal (Apache-2.0, Glance, week and month) or Etar master (GPL-3.0, RemoteViews, a real 7-cell week).
- **Work:** replace the data layer with HA, add icons and category lines, make taps configurable, strip a full calendar app.
- **Effort:** roughly 5 to 10 days, plus ongoing merges against an upstream with a different goal.
- **Risk:** carrying 13k lines of widgets for two of them (KashCal), or building on code merged two weeks ago and never released (Etar). KashCal's week is a list, not our tile.

### (c) Own widget

**(c1) Standalone small app.** Two widgets, one settings screen, one HA client with a cache.

- **Effort:** roughly 6 to 12 days. No upstream to follow.
- **Risk:** Android widget limits and launcher differences are ours to solve; widget update timing has to be tuned on the real phones.

**(c2) Inside the official HA app, as a PR.**

- **Effort:** (c1) plus weeks to months of review, and a private build until merge.
- **Risk:** the generic calendar entity has no kind, drawing or person colour, so an upstream widget would not look like ours without features upstream has no reason to take. `AI_POLICY.md` also requires you personally to explain every change and answer reviewers. The interim build costs a second login and Firebase push.

### Recommendation

**(c1), a small standalone Kotlin app that reads a payload HA publishes**, with the last payload cached so the widgets keep showing when there is no network. It is the only path that meets all three requirements: same look, one source for the rules, taps into HA. (b) is the alternative if a working calendar UI from day one matters more than a small codebase.

## 8. What could not be verified

- Any behaviour on a real phone: rendering, launcher differences, refresh latency, battery.
- Whether a query string reaches the custom card inside the app, and how a deep link behaves when the app is already open.
- `PROVIDER_CHANGED` delivery after a DAVx5 sync on current Android and OEM builds.
- Closed apps: whether DAVx5 calendars appear in Business Calendar 2, what day and event taps do there, whether any fires a URL.
- KWGT: bearer auth for HA, `homeassistant://` through "open Url".
- Real parcel sizes of a 42-cell RemoteViews layout.
- Which limited-distribution cap (20 or 50) is current, and the 2027 month.
