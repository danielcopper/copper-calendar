package dev.cupr.calendar.week

import dev.cupr.calendar.days.CalendarDays
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The week's rows' rules, ported from the dashboard's `calendar_days` as its
 * tile calls it (`empty`, `past`, `runs`) and its `calendar_lines` (the
 * dashboard's files and commits are named in PhoneMonth), and the days a week widget shows.
 */
class WeekLayoutTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val now = today.atTime(9, 0)
    private val dates = WeekDays.MONDAY_TO_SUNDAY.dates(today)

    private fun date(day: Int) = LocalDate.of(2026, 11, day)

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ): LocalDateTime = date(day).atTime(hour, minute)

    private fun timed(
        start: LocalDateTime,
        end: LocalDateTime,
        title: String = "Termin",
    ) = Entry(start = start, end = end, allDay = false, title = title, calendar = SampleData.FAMILY)

    private fun allDay(
        first: LocalDate,
        days: Long = 1,
        title: String = "Ganztags",
        kind: String? = null,
    ) = Entry(
        start = first.atStartOfDay(),
        end = first.plusDays(days).atStartOfDay(),
        allDay = true,
        title = title,
        calendar = SampleData.FAMILY,
        kind = kind,
    )

    private fun build(vararg entries: Entry) = WeekLayout.build(dates, entries.toList(), now)

    private fun List<WeekDay>.row(
        day: Int,
        title: String,
    ) = on(day).entries.single { it.entry.title == title }

    private fun List<WeekDay>.titles(day: Int) = on(day).entries.map { it.entry.title }

    private fun List<WeekDay>.on(day: Int) = single { it.date == date(day) }

    @Test
    fun `the days are Monday to Sunday of today's week, or today and the six days after it`() {
        assertEquals((9..15).map(::date), WeekDays.MONDAY_TO_SUNDAY.dates(today))
        assertEquals((11..17).map(::date), WeekDays.FROM_TODAY.dates(today))
        assertEquals((9..15).map(::date), WeekDays.MONDAY_TO_SUNDAY.dates(date(9)))
        assertEquals((9..15).map(::date), WeekDays.MONDAY_TO_SUNDAY.dates(date(15)))
    }

    @Test
    fun `calendar_days with empty shows every day, and marks today, the days before it and the weekend`() {
        val week = build()
        assertEquals(dates, week.map { it.date })
        assertTrue(week.all { it.entries.isEmpty() })
        assertEquals(listOf(true, true, false, false, false, false, false), week.map { it.past })
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.today })
        assertEquals(listOf(false, false, false, false, false, true, true), week.map { it.weekend })
    }

    @Test
    fun `calendar_days with past keeps the entries of the days before today, and leaves out one that ended before`() {
        val week = build(timed(at(9, 10), at(9, 11), "Montag"), timed(at(8, 10), at(8, 11), "Sonntag davor"))
        assertEquals(listOf("Montag"), week.on(9).entries.map { it.entry.title })
        assertTrue(week.flatMap { it.entries }.none { it.entry.title == "Sonntag davor" })
    }

    @Test
    fun `calendar_days puts the all-day entries first, then the timed ones by time, then by title`() {
        val week =
            build(
                timed(at(12, 15), at(12, 16), "B spät"),
                timed(at(12, 9), at(12, 10), "Z früh"),
                timed(at(12, 9), at(12, 10), "A früh"),
                allDay(date(12), title = "Ganz"),
            )
        assertEquals(listOf("Ganz", "A früh", "Z früh", "B spät"), week.on(12).entries.map { it.entry.title })
    }

    @Test
    fun `calendar_days gives a timed entry its start and end, or its start alone, and 24-00 at midnight`() {
        val week =
            build(
                timed(at(12, 16), at(12, 16, 45), "Kurs"),
                timed(at(12, 18), at(12, 18), "Punkt"),
                timed(at(12, 22), at(13, 0), "Bis Mitternacht"),
                allDay(date(12), title = "Ganz"),
            )
        val times = week.on(12).entries.associate { it.entry.title to it.time }
        assertEquals(RowTime.Within("16:00", "16:45"), times["Kurs"])
        assertEquals(RowTime.Within("18:00", null), times["Punkt"])
        assertEquals(RowTime.Within("22:00", "24:00"), times["Bis Mitternacht"])
        assertNull("an all-day row has no time", times["Ganz"])
        assertNull(
            week
                .on(12)
                .entries
                .single { it.entry.title == "Bis Mitternacht" }
                .addition,
        )
    }

    @Test
    fun `a timed entry over three days has a row on each, from its start, its place among its days, until its end`() {
        val week = build(timed(at(12, 9), at(14, 16), "Messe"))
        assertEquals(RowTime.From("09:00"), week.row(12, "Messe").time)
        assertEquals(RowTime.DayOf(2, 3), week.row(13, "Messe").time)
        assertEquals(RowTime.Until(End.At("16:00")), week.row(14, "Messe").time)
        assertTrue(week.on(15).entries.isEmpty())
        assertEquals(listOf(false, true, true), (12..14).map { week.row(it, "Messe").continued })
        assertTrue("no \"bis …\" after its title", (12..14).all { week.row(it, "Messe").addition == null })
        assertTrue((12..14).all { week.row(it, "Messe").timed })
    }

    @Test
    fun `a timed entry that began before the first day counts its days from its first, and ends on its last`() {
        val week = build(timed(at(7, 10), at(11, 12), "Kur"), timed(at(8, 20), at(9, 1, 30), "Nachtschicht"))
        assertEquals(RowTime.DayOf(3, 5), week.row(9, "Kur").time)
        assertEquals(RowTime.DayOf(4, 5), week.row(10, "Kur").time)
        assertEquals(RowTime.Until(End.At("12:00")), week.row(11, "Kur").time)
        assertFalse("it stands on the first day shown as its own", week.row(9, "Kur").continued)
        assertEquals(RowTime.Until(End.At("01:30")), week.row(9, "Nachtschicht").time)
    }

    @Test
    fun `a timed entry that ends at midnight has the day before as its last, until 24-00`() {
        val week = build(timed(at(12, 20), date(14).atStartOfDay(), "Fest"))
        assertEquals(RowTime.From("20:00"), week.row(12, "Fest").time)
        assertEquals(RowTime.Until(End.At("24:00")), week.row(13, "Fest").time)
        assertTrue(week.on(14).entries.isEmpty())
    }

    @Test
    fun `an entry that runs on after the last day shown names its end on its last row`() {
        val week =
            build(
                timed(at(14, 9), at(17, 18), "Reise"),
                timed(at(13, 20), date(17).atStartOfDay(), "Nachtfahrt"),
                allDay(date(14), days = 48, title = "Ferien"),
                timed(at(15, 8), at(19, 18), "Kongress"),
            )
        assertEquals(RowTime.From("09:00"), week.row(14, "Reise").time)
        assertEquals(
            "instead of Tag 2 von 4",
            RowTime.Until(End.Later(date(17), true, "18:00")),
            week.row(15, "Reise").time,
        )
        assertEquals(RowTime.Until(End.Later(date(16), true, null)), week.row(15, "Nachtfahrt").time)
        assertEquals(RowTime.Until(End.Later(LocalDate.of(2026, 12, 31), false, null)), week.row(15, "Ferien").time)
        assertNull(week.row(14, "Ferien").time)
        assertEquals(RowTime.FromTo("08:00", End.Later(date(19), true, "18:00")), week.row(15, "Kongress").time)
    }

    @Test
    fun `an end seven days after the row's day is named by its date, six days after by its weekday`() {
        val dates = WeekDays.FROM_TODAY.dates(today)
        val seven = timed(at(16, 9), at(24, 9), "Sieben")
        val six = timed(at(16, 9), at(23, 9), "Sechs")
        val week = WeekLayout.build(dates, listOf(seven, six), now)
        assertEquals(RowTime.Until(End.Later(date(24), false, "09:00")), week.row(17, "Sieben").time)
        assertEquals(RowTime.Until(End.Later(date(23), true, "09:00")), week.row(17, "Sechs").time)
    }

    @Test
    fun `an all-day entry over several days is an all-day row on each, without a time or addition`() {
        val week =
            build(
                allDay(date(12), days = 3, title = "Ausflug"),
                allDay(date(12), days = 2, title = "Feiertage", kind = Kinds.HOLIDAY),
            )
        for (day in 12..14) {
            val row = week.row(day, "Ausflug")
            assertNull(row.time)
            assertNull(row.addition)
            assertEquals(day > 12, row.continued)
        }
        assertTrue(week.on(15).entries.isEmpty())
        assertNull(week.row(12, "Feiertage").addition)
        assertTrue(week.on(12).holiday)
        assertFalse("a holiday continued from an earlier day does not red the day", week.on(13).holiday)
    }

    @Test
    fun `school holidays that began before the first day stand on it, and have a row on each further day`() {
        val week = build(allDay(date(2), days = 10, title = "Herbstferien", kind = Kinds.SCHOOL_HOLIDAYS))
        assertEquals(listOf("Herbstferien"), week.titles(9))
        assertEquals(listOf(false, true, true), (9..11).map { week.row(it, "Herbstferien").continued })
        assertTrue(week.on(12).entries.isEmpty())
    }

    @Test
    fun `a day's continued all-day rows go before its own by title, then its continued timed rows before its own`() {
        val week =
            build(
                timed(at(13, 7), at(13, 8), "Früh"),
                timed(at(12, 22), at(13, 6), "Nachtschicht"),
                timed(at(12, 21), at(13, 9), "Spätdienst"),
                allDay(date(13), title = "Arzt"),
                allDay(date(12), days = 2, title = "Zelten"),
            )
        assertEquals(listOf("Zelten", "Arzt", "Spätdienst", "Nachtschicht", "Früh"), week.titles(13))
    }

    @Test
    fun `an all-day row's only time is a later last day it names, without a time of day`() {
        val entry = allDay(date(12), days = 5)
        val row = { time: RowTime -> DayEntry(entry, time, null, continued = false, over = false) }
        row(RowTime.Until(End.Later(date(16), true, null)))
        for (time in listOf(
            RowTime.Within("09:00", null),
            RowTime.Until(End.At("24:00")),
            RowTime.Until(End.Later(date(16), true, "18:00")),
        )) {
            assertThrows("$time", IllegalArgumentException::class.java) { row(time) }
        }
    }

    @Test
    fun `continued all-day rows keep the order they are given in, as the tile's do`() {
        val week = build(allDay(date(12), days = 2, title = "Zelten"), allDay(date(12), days = 2, title = "Ausflug"))
        assertEquals(listOf("Ausflug", "Zelten"), week.titles(12))
        assertEquals(listOf("Zelten", "Ausflug"), week.titles(13))
    }

    @Test
    fun `on the first day shown, an entry that began before it sorts among the day's own rows`() {
        val week =
            build(
                timed(at(9, 8), at(9, 9), "Sport"),
                timed(at(8, 20), at(9, 1, 30), "Nachtschicht"),
                allDay(date(9), title = "Zoo"),
                allDay(date(9), title = "Ausflug"),
                allDay(date(7), days = 3, title = "Messe"),
            )
        assertEquals(listOf("Ausflug", "Messe", "Zoo", "Nachtschicht", "Sport"), week.titles(9))
    }

    @Test
    fun `today's timed rows whose end has passed are over, at the time the week is built`() {
        val noon = at(11, 12)
        val week =
            WeekLayout.build(
                dates,
                listOf(
                    timed(at(11, 9), at(11, 10), "Vorbei"),
                    timed(at(11, 12), at(11, 12), "Jetzt"),
                    timed(at(11, 11), at(11, 13), "Läuft"),
                    timed(at(10, 9), at(10, 10), "Gestern"),
                    timed(at(10, 20), at(11, 8), "Nacht"),
                    allDay(date(11), title = "Ganz"),
                ),
                noon,
            )
        // Each entry once: the night's row on the day before today is not over.
        assertEquals(
            listOf("Nacht", "Vorbei", "Jetzt"),
            week.flatMap { it.entries }.filter { it.over }.map { it.entry.title },
        )
        assertTrue("a day before today is dimmed whole instead", week.on(10).past)
    }

    @Test
    fun `calendar_days says Feiertag for a public holiday and reds its day, and a birthday's age`() {
        val week =
            build(
                allDay(date(12), title = "Feiertag", kind = Kinds.HOLIDAY),
                allDay(date(13), title = "Max", kind = Kinds.BIRTHDAY).copy(year = 2000),
                allDay(date(14), title = "Ohne Jahr", kind = Kinds.BIRTHDAY),
            )
        assertEquals(
            Addition.Holiday,
            week
                .on(12)
                .entries
                .single()
                .addition,
        )
        assertTrue(week.on(12).holiday)
        assertFalse(week.on(13).holiday)
        assertEquals(
            Addition.Turns(26),
            week
                .on(13)
                .entries
                .single()
                .addition,
        )
        assertNull(
            week
                .on(14)
                .entries
                .single()
                .addition,
        )
    }

    @Test
    fun `a birthday whose year of birth lies after it gets no age`() {
        val week = build(allDay(date(13), title = "Falsch", kind = Kinds.BIRTHDAY).copy(year = 2030))
        assertNull(
            week
                .on(13)
                .entries
                .single()
                .addition,
        )
        assertThrows(IllegalArgumentException::class.java) { Addition.Turns(-1) }
    }

    @Test
    fun `a timed entry of no length at the first day's midnight is left out, unlike in the month`() {
        val midnight = date(9).atStartOfDay()
        assertTrue(build(timed(midnight, midnight, "Mitternacht")).all { it.entries.isEmpty() })
    }

    @Test
    fun `the days run on without a gap`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                WeekLayout.build(listOf(date(9), date(11)), emptyList(), now)
            }
        assertTrue("${error.message}", error.message!!.startsWith("The days run on without a gap"))
        assertThrows(IllegalArgumentException::class.java) { WeekLayout.build(dates.reversed(), emptyList(), now) }
        assertThrows(IllegalArgumentException::class.java) { WeekLayout.build(emptyList(), emptyList(), now) }
    }

    @Test
    fun `a day leaves out its last entries for a line that counts them, and stays a holiday`() {
        val day = build(allDay(date(12), title = "Feiertag", kind = Kinds.HOLIDAY), timed(at(12, 9), at(12, 10))).on(12)
        val capped = day.copy(more = 2)
        assertTrue(capped.shown.isEmpty())
        assertTrue(capped.holiday)
        assertEquals(listOf("Feiertag"), day.copy(more = 1).shown.map { it.entry.title })
        assertThrows(IllegalArgumentException::class.java) { day.copy(more = 3) }
        assertThrows(IllegalArgumentException::class.java) { day.copy(more = -1) }
        assertThrows(IllegalStateException::class.java) { day.copy(more = 2).leaveOutLast() }
        assertEquals(1, day.leaveOutLast().more)
    }

    @Test
    fun `an entry stands on its first day or after it, never before`() {
        val entry = timed(at(12, 9), at(12, 10))
        assertEquals(date(12), CalendarDays.Placed(entry, date(12)).date)
        assertThrows(IllegalArgumentException::class.java) { CalendarDays.Placed(entry, date(11)) }
    }

    @Test
    fun `an entry after the last day is left out`() {
        assertTrue(build(timed(at(16, 10), at(16, 11))).all { it.entries.isEmpty() })
    }

    @Test
    fun `the range names both days, the month once when they share it`() {
        assertEquals("9.–15. Nov.", WeekLayout.range(date(9), date(15)))
        assertEquals("30. Nov. – 6. Dez.", WeekLayout.range(date(30), LocalDate.of(2026, 12, 6)))
    }

    @Test
    fun `in the sample week Saturday is full, Sunday free, and Friday's birthday turns 26`() {
        val week = WeekLayout.build(dates, SampleData.entries, SampleData.now)
        assertEquals(8, week.on(14).entries.size)
        assertTrue(week.on(15).entries.isEmpty())
        assertEquals(
            Addition.Turns(26),
            week
                .on(13)
                .entries
                .single()
                .addition,
        )
        val wednesday = week.on(11).entries
        assertEquals(listOf("Gelber Sack", "Martinstag", "Martinsumzug Kita"), wednesday.map { it.entry.title })
        assertNull("a special day has no UID", wednesday[1].entry.uid)
        assertTrue(wednesday[0].entry.uid != null && wednesday[2].entry.uid != null)
    }
}
