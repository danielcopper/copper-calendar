# Design

Mockups: [Kalender-Widgets Mockups](https://claude.ai/artifact/6R9mUH9GZJXXdV5Uf3bJK9), with week as rows, week as columns after the idle page (rejected, D14), month, and two compact column layouts ("Text klein" chosen, D19; "Nur Symbole" dropped). Values come from `homelab` `infra/homeassistant/config/` (`themes/zuhause.yaml`, `dashboards/zuhause.yaml`); events in the mockups are sample data.

More configuration than the calendar widgets on the market is a goal: what a person might want to set differently is a setting per widget, with Home Assistant's look as the default.

## Widgets

Three widgets in the widget picker, under the app's name "Copper Calendar" (D31); the user picks one when adding it (D19).

| Widget | Model in Home Assistant | Content |
|---|---|---|
| "Woche" | tile "Kalender · diese Woche" (template `calendar`) | Monday to Sunday, or today and the six days after it (D20), one row per day: weekday and date at the left ("HEUTE" for today, tinted), the day's entries at the right, timed ones with their time and mark, all-day ones as tinted chips, with their additions ("wird 26", "bis …", "Feiertag", D18), an empty day "frei", the titles of entries running on from an earlier day in a muted line; days before today dimmed; narrower than 4 cells the weekday stands above the date, today's too instead of "HEUTE", and a timed entry's time on a line above its mark and title (D39); a day too full for the bitmap limit ends in "+1 weiterer" or "+N weitere" (D40); 4 × 4 cells when added, from 2 cells wide (D34) |
| "Woche quer" (D19) | a week row of the month below, with every entry of a day | the same days as "Woche", seven columns under weekday heads from the first day on; per entry its mark and title on one line at 10 px, no "+N" unless a day is too full for the bitmap limit (D40); a special day with no room beside the number among the entries; 4 × 2 cells when added, from 4 cells wide (D34) |
| "Monat" | month of the calendar page at phone size (`calendar_month_view`, `.s-phone`) | weeks from the Monday before the first to the Sunday after the last; per day its number, special days as drawings (one without a drawing as its icon or a dot, D24), smaller where the cell has less room (D38), up to two entries or one and "+N", which also counts the special days and public holidays with no room beside the number (D24); entries over several days as bars |

The widgets draw the snapshot of Home Assistant's last good answer (D2, D6); without one, a plain text on the card says "Nicht angemeldet – zum Anmelden tippen" or, signed in before the first snapshot, "Noch keine Daten – zum Öffnen tippen" (D50). All follow the system's dark or light mode with the two palettes of the theme "Zuhause" from the snapshot, or built-in ones without it; which parts keep the built-in colours whatever the theme says: D48. An entry without its own colour takes its calendar's from the theme (D49); categories are not drawn (D53). "Woche" and "Monat" are a vertically scrolling list of drawn rows (D8): "Woche" a day per row, "Monat" a week per row. "Woche quer" is one drawn row as tall as its fullest day, which scrolls when the widget is lower; a fixed fade above the list's bottom edge shows then, and the row ends in blank room as tall as the fade (D35). A widget the system still refuses for its bitmaps, or whose drawing fails, shows a plain text that opens the calendar page (D40). The app updates the widgets after each fetch, a new sign-in, signing out and at midnight (D54), the launcher every 30 minutes and for a new or resized widget. By default each update brings today's row flush to the top of "Woche" and "Monat": the week's current day, the month's current week; when the rows from today on fit, the list scrolls to its end instead (D27). Each widget's setup can make that only at midnight, never, or every 15, 30 or 60 minutes; a redraw that does not scroll leaves the list where the user scrolled it, and a list built anew shows today on top from its next update, whatever is set (D27, D59). Sizes are resizable; the mockups use 4×2 and 4×4 cells (276 × 220 and 276 × 456 dp by Android's table for a Pixel 4).

A header stands above the list: the month's name, or for a week widget "DIESE WOCHE" or "NÄCHSTE 7 TAGE" with its days' range at the right ("9.–15. Nov.", cut with "…" where it has no room, D36), and the chevron; the month and "Woche quer" have weekday heads under it. Once the last successful fetch is more than an hour old, the right side says instead, muted, when it was: "Stand 14:30", "Stand gestern 18:30" or "Stand 9. Nov." (D51); while the sign-in is no longer usable, "Neu anmelden" in the accent colour (D52). A round "+" button (40 dp, accent colour, as the calendar page's 56 px button) floats above the list at the bottom right, or at the bottom left as set per widget (D58).

In the widget picker, each widget shows a fixed image of itself at its default size, drawn from the sample data in light mode (D33).

## Launcher icon

A calendar sheet with two binder rings and a grid of days, today marked, in one colour: dark blue on light blue in light mode, light on dark blue in dark mode, and the sheet with the days cut out for themed icons (D32).

## Taps

Each widget has its own tap targets. A target is a Home Assistant path opened through `homeassistant://navigate/…` (with `{day}` replaced by the tapped day), another app, or nothing.

| Element | Default target | Notes |
|---|---|---|
| Header | `/dashboard-zuhause/kalender` | the sign-in screen while the header says "Neu anmelden" (D52) |
| Day (week row, week column, month cell) | `/dashboard-zuhause/kalender?day={day}` | on the phone, a day with entries opens its list (D15) |
| Entry ("Woche" only) | `/dashboard-zuhause/kalender?entry={uid}&day={day}` | needs the UID in the sensor data (D16); an entry without one opens its day (D37); month cells and the columns of "Woche quer" have no entry areas |
| "+" | `/dashboard-zuhause/kalender?new={day}` | `{day}` is today |
| Plain text without data | the sign-in screen | not configurable (D50) |

The calendar page reads `?day=`, `?entry=…&day=` and `?new=`, and all three work through the Home Assistant app (checked on the phone, 2026-11-10); see [ha-additions.md](ha-additions.md).

A tap on a Home Assistant target first starts the Home Assistant app as its icon does, in a new task that replaces its old one, and then opens the target on top of it (D30): back from the target returns to the page the app itself opens with, instead of stepping through the pages of earlier taps, and whatever was open in the app before the tap is gone. A target in another app opens alone, in a new task.

## Setup app

Per widget: which calendars it shows (D11), the tap targets above, the corner of "+", for the week widgets their days, Monday to Sunday or from today (D20), and when it returns to today (D27). The week's layout is not a setting: rows and columns are two widgets (D19). Once for the app: sign-in with Home Assistant (D9, D10) and how often it fetches (D60).

## Setup screen

A widget's own screen, opened from the launcher's long-press menu; placing a widget opens nothing (D56). In the phone's own colours, light or dark with the system, as the sign-in screen. Without stored settings a widget has the defaults (D57).

- Title "Widget „Monat“", "Widget „Woche“" or "Widget „Woche quer“".
- "Kalender": a box per calendar, ticked unless the widget leaves it out; by name in the dashboard's order once the sensor lists its calendars, until then by id (D57); with none known, "Keine Kalender bekannt".
- "Tage" ("Woche", "Woche quer"): "Montag bis Sonntag" or "Heute und die sechs Tage danach" (D20).
- "Zurück zu heute" ("Monat", "Woche"): "Bei jeder Aktualisierung", "Nur um Mitternacht", "Nie", "Alle 15 Minuten", "Alle 30 Minuten", "Alle 60 Minuten" (D27, D59).
- "Ecke für „+“": "Unten rechts" or "Unten links" (D58).
- "Speichern" stores the settings and redraws the widget at once, the month's and "Woche"'s list back at today, whatever "Zurück zu heute" says (D59); back leaves the stored settings as they were.

## Sign-in screen

The app's screen opened from its launcher icon, in the phone's own colours, light or dark with the system (D41).

- Signed out: a field for the Home Assistant address, kept once entered (D42), and "Mit Home Assistant anmelden", which shows Home Assistant's own login page inside the app (D43). When the page does not load or its certificate is not trusted, "Home Assistant nicht erreichbar – Adresse und Verbindung prüfen"; when the sign-in does not come about for another reason, "Anmeldung fehlgeschlagen – bitte noch einmal versuchen"; going back from the page says nothing.
- Signed in: "Angemeldet als …, letzter Abruf um …" (on an earlier day "am 11. November um …"), or "Angemeldet, noch kein Abruf", "Abmelden" (D47), and under it "Abruf": "Alle 15 Minuten", "Alle 30 Minuten" or "Alle 60 Minuten" (D60). While the last fetch failed, a line under the status says "Letzter Abruf fehlgeschlagen um …: " and its cause in plain words, such as "kein Netz" or "Sensor nicht gefunden" (D46). When a fetch could not read the theme, a line under that says "Theme „Zuhause“ nicht lesbar – Farben vom Abruf um …", or "– eingebaute Farben" without a theme kept; how long: D48.
- Refused by Home Assistant: "Home Assistant hat die Anmeldung abgelehnt – bitte neu anmelden"; the stored sign-in not readable on this phone: "Gespeicherte Anmeldung auf diesem Handy nicht lesbar – bitte neu anmelden"; each with the field and the button (D45, D46).

Each time the screen opens while signed in, the app fetches (D46).
