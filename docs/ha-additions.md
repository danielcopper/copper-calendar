# Proposed Home Assistant additions

Proposals for `homelab` (`infra/homeassistant/`), which this repository only reads. Line references are to `homelab` `main` at `24b4a670`.

## 1. Sensor window from the month's first Monday (D16)

`sensor.family_calendar` asks from the Monday of the current week (`packages/kalender.yaml`, `start_date_time`, 67 days). The month widget needs the Monday before the first of the current month, so its first weeks are not empty. The end stays at least 60 days after today. The special days and the school start need the same start (`calendar_entries`, `first` and `days`). The tile and the idle page select their own days, so they are not affected; the list grows by up to five weeks.

## 2. UID per entry, colour and categories by UID (D16)

`calendar.get_events` returns no UID, so `sensor.family_calendar` matches own colour and categories by calendar and title (`homelab` README, "Kalenderfarben aus Nextcloud"). Two events of the same title in one calendar with different own colours get none, and an individually recoloured instance of a series shows the series' colour; a widget cannot open an entry. The data the widgets read should carry each entry's UID and match colour and categories by UID, as the calendar page does. How (a better sensor or another source) is up to `homelab`; the request went to its session on 2026-11-09.

## 3. Fields the widgets need

| Field | Today | Proposal |
|---|---|---|
| Icon per kind | only in the dashboard (`calendar_icons`, `zuhause.yaml:1617`) | one home in `zuhause.jinja`, the entry carries its icon name, the dashboard reads it from there |
| Final colour | `color` only for an own or a person's colour; otherwise the Nextcloud calendar colour (`sensor.nextcloud_calendar_colors`) or a theme colour that differs by mode (`zuhause-calendar-*`) | open: see below |
| Addition text | "wird 26", "bis …", "Feiertag" computed in the dashboard's JavaScript (`calendar_days`, `zuhause.yaml:1720`) | none: the app derives it from `year`, `start`, `end` and `kind` (D18), so these fields stay in the data |

Open point on colour: the fixed calendar colours live in the theme, which a template cannot read, and the app needs the whole palette (card, text, divider, accent, calendar colours) for both modes anyway. The WebSocket command `frontend/get_themes` returns every theme with its `modes` to any signed-in user (no `require_admin`, `components/frontend/__init__.py:992` at Core `2026.10.4`), so the app could read the theme "Zuhause" from Home Assistant instead of keeping a copy.

## 4. Calendar page reads its URL (D15)

- `?day=YYYY-MM-DD` selects that day; on the phone a day with entries opens its list (`calendar_list`).
- `?entry=<uid>&day=YYYY-MM-DD` opens that entry's sheet on that day.
- `?new=YYYY-MM-DD` opens the form for a new entry on that day.

Checked on the phone (2026-11-10): all three reach the page's card through the Android app's `homeassistant://navigate/…` links.

## 5. Each entry's resolved colour (D49)

A suggestion; `homelab` decides. The sensor's `color` holds an entry's own colour or its person's only. Without one, the dashboard takes the calendar's colour in Nextcloud (`sensor.nextcloud_calendar_colors`), except for the birthdays, the school holidays, the public holidays and the special days, and then the theme's colour by the table `calendar_colors` (`dashboards/templates.yaml` at `64054110`, `calendar_color`). The app reads neither the Nextcloud colours nor that table, so it keeps its own copy of the table, with the family calendar matched by the start of its id, and an entry of a calendar coloured only in Nextcloud can look different in a widget than on the dashboard.

If the sensor delivered each entry's resolved colour (own, else person, else the calendar's in Nextcloud), and for the calendars the theme colours the name of their theme variable instead of a colour, since the theme differs by mode, the app could drop its table and draw what the dashboard draws.

## 6. The sensor's calendars (D57)

A widget's setup lists the calendars the widget can leave out. Without a list from Home Assistant the app knows only the calendars seen in the snapshot's entries, by id, and a calendar without an entry in the sensor's window is missing unless the widget leaves it out. The proposal: `sensor.family_calendar` gets an attribute `calendars`, the calendars it reads in the dashboard's order, the special days included, each `{id, name}`: `id` as the entries' `calendar` names it, `name` as Home Assistant shows it. The request went to `homelab`'s session on 2026-11-16; until its form is final the app reads it leniently (D57).
