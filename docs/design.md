# Design

Mockups: [Kalender-Widgets Mockups](https://claude.ai/artifact/6R9mUH9GZJXXdV5Uf3bJK9), with week as rows, week as columns after the idle page (rejected, D14), month, and two compact column layouts ("Text klein" chosen, D19; "Nur Symbole" dropped). Values come from `homelab` `infra/homeassistant/config/` (`themes/zuhause.yaml`, `dashboards/zuhause.yaml`); events in the mockups are sample data.

More configuration than the calendar widgets on the market is a goal: what a person might want to set differently is a setting per widget, with Home Assistant's look as the default.

## Widgets

Three widgets in the widget picker, under the app's name "Copper Calendar" (D31); the user picks one when adding it (D19).

| Widget | Model in Home Assistant | Content |
|---|---|---|
| "Woche" | tile "Kalender · diese Woche" (template `calendar`) | Monday to Sunday, or today and the six days after it (D20), one row per day: weekday and date at the left ("HEUTE" for today), the day's entries at the right, an empty day "frei"; days before today dimmed; from 3 cells wide |
| "Woche quer" (D19) | a week row of the month below, with every entry of a day | the same days as "Woche", seven columns; per entry its mark and title on one line at 10 px |
| "Monat" | month of the calendar page at phone size (`calendar_month_view`, `.s-phone`) | weeks from the Monday before the first to the Sunday after the last; per day its number, special days as drawings, up to two entries or one and "+N"; entries over several days as bars |

All follow the system's dark or light mode with the theme's two palettes. "Woche" and "Monat" are a vertically scrolling list of drawn rows (D8): "Woche" a day per row, "Monat" a week per row. Once the app fetches from Home Assistant, each update brings today's row to the top by default: the week's current day, the month's current week (D27). Sizes are resizable; the mockups use 4×2 and 4×4 cells (276 × 220 and 276 × 456 dp by Android's table for a Pixel 4).

A round "+" button (40 dp, accent colour, as the calendar page's 56 px button) floats above the list at the bottom right; its corner is configurable per widget.

In the widget picker, "Monat" shows a fixed image of itself drawn from the sample month in light mode (D33).

## Launcher icon

A calendar sheet with two binder rings and a grid of days, today marked, in one colour: dark blue on light blue in light mode, light on dark blue in dark mode, and the sheet with the days cut out for themed icons (D32).

## Taps

Each widget has its own tap targets. A target is a Home Assistant path opened through `homeassistant://navigate/…` (with `{day}` replaced by the tapped day), another app, or nothing.

| Element | Default target | Notes |
|---|---|---|
| Header | `/dashboard-zuhause/kalender` | |
| Day (week row, month cell) | `/dashboard-zuhause/kalender?day={day}` | on the phone, a day with entries opens its list (D15) |
| Entry (week only) | `/dashboard-zuhause/kalender?entry={uid}&day={day}` | needs the UID in the sensor data (D16); month cells have no entry areas |
| "+" | `/dashboard-zuhause/kalender?new={day}` | `{day}` is today |

The `?day=` and `?new=` parameters do not exist yet; see [ha-additions.md](ha-additions.md).

A target opens in a new task that replaces the Home Assistant app's (D30): back then leaves Home Assistant instead of stepping through the pages of earlier taps, and whatever was open in the app before the tap is gone.

## Setup app

Per widget: which calendars it shows (D11), the tap targets above, the corner of "+", for the week widgets their days, Monday to Sunday or from today (D20), and when it returns to today (D27). The week's layout is not a setting: rows and columns are two widgets (D19). Once for the app: sign-in with Home Assistant (D9, D10).
