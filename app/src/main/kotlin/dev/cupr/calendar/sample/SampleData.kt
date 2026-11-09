package dev.cupr.calendar.sample

import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Palette
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * SAMPLE DATA, not real data. The entries of a sample month and the two
 * palettes of the theme "Zuhause" (its file is named in
 * [dev.cupr.calendar.render.PhoneMonth]). They stand in until Home Assistant
 * delivers entries (D16) and the theme (D17); the widget and the tests both
 * draw from them.
 */
object SampleData {
    val zone: ZoneId = ZoneId.of("Europe/Berlin")

    /** "Today" of the sample month: Wednesday, 11 November 2026, 09:00. */
    val clock: Clock =
        Clock.fixed(LocalDateTime.of(2026, 11, 11, 9, 0).atZone(zone).toInstant(), zone)

    val month: YearMonth = YearMonth.of(2026, 11)

    // Calendars. The family calendar's id is neutral here; the fixed ones
    // are Home Assistant's (the dashboard's calendar_colors).
    const val FAMILY = "calendar.family"
    const val BIRTHDAYS = "calendar.contact_birthdays"
    const val SCHOOL_HOLIDAYS = "calendar.schulferien_hessen"
    const val PUBLIC_HOLIDAYS = "calendar.deutschland_he"
    const val SPECIAL = "special"

    // The entries' own colours: hotpink, rebeccapurple, lightskyblue, steelblue.
    private const val HOTPINK = 0xFFFF69B4.toInt()
    private const val REBECCAPURPLE = 0xFF663399.toInt()
    private const val LIGHTSKYBLUE = 0xFF87CEFA.toInt()
    private const val STEELBLUE = 0xFF4682B4.toInt()

    val dark =
        Palette(
            card = 0xFF1A1F25.toInt(),
            cardBorder = 0xFF262C34.toInt(),
            text = 0xFFECEEF1.toInt(),
            secondaryText = 0xFFA3ACB7.toInt(),
            tertiaryText = 0xFF7D8793.toInt(),
            divider = 0xFF262C34.toInt(),
            accent = 0xFF6FB1FF.toInt(),
            onAccent = 0xFF0D1A2A.toInt(),
            holiday = 0xFFFF7A7A.toInt(),
            // rgba(0, 0, 0, 0.5)
            shadow = 0x80000000.toInt(),
            calendars =
                mapOf(
                    FAMILY to 0xFF3CB371.toInt(),
                    BIRTHDAYS to 0xFFFFD700.toInt(),
                    SCHOOL_HOLIDAYS to 0xFF40E0D0.toInt(),
                    PUBLIC_HOLIDAYS to 0xFFA9A9A9.toInt(),
                    SPECIAL to 0xFFFF8C00.toInt(),
                ),
        )

    val light =
        Palette(
            card = 0xFFFFFFFF.toInt(),
            cardBorder = 0xFFDDE2E9.toInt(),
            text = 0xFF161B22.toInt(),
            secondaryText = 0xFF5B6573.toInt(),
            tertiaryText = 0xFF666E7A.toInt(),
            divider = 0xFFE6EAF0.toInt(),
            accent = 0xFF1F6FD6.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            holiday = 0xFFB52222.toInt(),
            // rgba(22, 27, 34, 0.18)
            shadow = 0x2E161B22,
            calendars =
                mapOf(
                    FAMILY to 0xFF359F65.toInt(),
                    BIRTHDAYS to 0xFFA38A00.toInt(),
                    SCHOOL_HOLIDAYS to 0xFF2D9D92.toInt(),
                    PUBLIC_HOLIDAYS to 0xFF8C8C8C.toInt(),
                    SPECIAL to 0xFFD67500.toInt(),
                ),
        )

    val entries: List<Entry> =
        buildList {
            for (day in listOf(26, 2, 9, 16, 23, 30)) {
                val date = if (day == 26) LocalDate.of(2026, 10, 26) else LocalDate.of(2026, 11, day)
                add(timed(date, "16:00", "16:45", "Schwimmkurs Anna", HOTPINK, "swimming", "mdi:swim"))
            }
            for (date in listOf(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 25))) {
                add(allDay(date, 1, "Gelber Sack", REBECCAPURPLE, "trash", "mdi:trash-can"))
            }
            add(special(LocalDate.of(2026, 10, 31), "Halloween", "halloween.svg"))
            add(special(LocalDate.of(2026, 11, 11), "Martinstag", "lantern.svg"))
            add(special(LocalDate.of(2026, 11, 29), "1. Advent", "candles-1.svg"))
            add(special(LocalDate.of(2026, 12, 6), "Nikolaus", "boot.svg"))
            add(
                timed(
                    LocalDate.of(2026, 11, 10),
                    "15:30",
                    "16:15",
                    "Zahnarzt Ben",
                    LIGHTSKYBLUE,
                    "dentist",
                    "mdi:tooth-outline",
                ),
            )
            add(
                timed(LocalDate.of(2026, 11, 11), "17:00", "18:30", "Martinsumzug Kita", REBECCAPURPLE, null, null)
                    .copy(drawing = "lantern.svg"),
            )
            add(
                timed(
                    LocalDate.of(2026, 11, 12),
                    "19:30",
                    "21:00",
                    "Elternabend Kita",
                    REBECCAPURPLE,
                    "parents_evening",
                    "mdi:account-group",
                ),
            )
            add(
                allDay(LocalDate.of(2026, 11, 13), 1, "Max Weber", null, "birthday", "mdi:cake-variant")
                    .copy(calendar = BIRTHDAYS, year = 2000),
            )
            add(
                timed(
                    LocalDate.of(2026, 11, 14),
                    "10:00",
                    "11:30",
                    "Fußball Ben",
                    LIGHTSKYBLUE,
                    "football",
                    "mdi:soccer",
                ),
            )
            add(
                timed(
                    LocalDate.of(2026, 11, 14),
                    "15:00",
                    "18:00",
                    "Kindergeburtstag Lena",
                    HOTPINK,
                    "party",
                    "mdi:party-popper",
                ),
            )
            add(
                allDay(
                    LocalDate.of(2026, 11, 20),
                    3,
                    "Kurzurlaub Ostsee",
                    REBECCAPURPLE,
                    "vacation",
                    "mdi:bag-suitcase",
                ),
            )
            add(allDay(LocalDate.of(2026, 11, 26), 2, "Dienstreise Hamburg", STEELBLUE, "work", "mdi:briefcase"))
            add(timed(LocalDate.of(2026, 11, 28), "10:00", "11:00", "Adventskranz binden", REBECCAPURPLE, null, null))
            add(
                timed(
                    LocalDate.of(2026, 11, 28),
                    "15:00",
                    "18:00",
                    "Weihnachtsmarkt",
                    REBECCAPURPLE,
                    "outing",
                    "mdi:compass-outline",
                ),
            )
            add(
                timed(
                    LocalDate.of(2026, 11, 28),
                    "18:30",
                    "20:00",
                    "Plätzchen backen",
                    HOTPINK,
                    "cooking",
                    "mdi:chef-hat",
                ),
            )
        }

    private fun timed(
        date: LocalDate,
        from: String,
        to: String,
        title: String,
        color: Int?,
        kind: String?,
        icon: String?,
    ) = Entry(
        start = date.atTime(LocalTime.parse(from)),
        end = date.atTime(LocalTime.parse(to)),
        allDay = false,
        title = title,
        calendar = FAMILY,
        kind = kind,
        color = color,
        icon = icon,
    )

    private fun allDay(
        date: LocalDate,
        days: Long,
        title: String,
        color: Int?,
        kind: String?,
        icon: String?,
    ) = Entry(
        start = date.atStartOfDay(),
        end = date.plusDays(days).atStartOfDay(),
        allDay = true,
        title = title,
        calendar = FAMILY,
        kind = kind,
        color = color,
        icon = icon,
    )

    private fun special(
        date: LocalDate,
        title: String,
        drawing: String,
    ) = allDay(date, 1, title, null, "special", null).copy(calendar = SPECIAL, drawing = drawing)
}
