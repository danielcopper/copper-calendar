package dev.cupr.calendar.week

import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The week's rows' rules, ported from the dashboard's `calendar_days` as its
 * tile calls it (`empty`, `past`, `runs`; the dashboard's file is named in
 * PhoneMonth), and the days a week widget shows.
 */
class WeekLayoutTest {
    private val today = LocalDate.of(2026, 11, 11)
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

    private fun build(vararg entries: Entry) = WeekLayout.build(dates, entries.toList(), today)

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
        assertTrue(week.all { it.entries.isEmpty() && it.running.isEmpty() })
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
        assertEquals("16:00–16:45", times["Kurs"])
        assertEquals("18:00", times["Punkt"])
        assertEquals("22:00–24:00", times["Bis Mitternacht"])
        assertNull("an all-day entry is a chip", times["Ganz"])
        assertNull(
            week
                .on(12)
                .entries
                .single { it.entry.title == "Bis Mitternacht" }
                .addition,
        )
    }

    @Test
    fun `calendar_days says until when an entry over several days ends, and carries its title on the days after`() {
        val week = build(allDay(date(12), days = 3, title = "Ausflug"), timed(at(14, 20), at(15, 1), "Nacht"))
        val trip = week.on(12).entries.single()
        assertNull(trip.time)
        assertEquals(Addition.Until("Samstag, 14. November"), trip.addition)
        assertEquals(listOf("Ausflug"), week.on(13).running)
        assertEquals(listOf("Ausflug"), week.on(14).running)
        assertTrue(week.on(13).entries.isEmpty())
        val night = week.on(14).entries.single { it.entry.title == "Nacht" }
        assertEquals("20:00", night.time)
        assertEquals(Addition.Until("Sonntag, 15. November"), night.addition)
        assertEquals(listOf("Nacht"), week.on(15).running)
    }

    @Test
    fun `calendar_days puts an entry that began before the first day on it, a timed one until its time`() {
        val week = build(timed(at(8, 20), at(9, 1, 30), "Nachtschicht"), allDay(date(7), days = 3, title = "Messe"))
        val monday = week.on(9).entries.associateBy { it.entry.title }
        assertNull("not timed on a day it began before", monday.getValue("Nachtschicht").time)
        assertEquals(Addition.Until("01:30"), monday.getValue("Nachtschicht").addition)
        assertEquals(Addition.Until("Montag, 9. November"), monday.getValue("Messe").addition)
    }

    @Test
    fun `calendar_days puts school holidays that began before the first day on the first day shown, then runs them`() {
        val week = build(allDay(date(2), days = 10, title = "Herbstferien", kind = Kinds.SCHOOL_HOLIDAYS))
        assertEquals(listOf("Herbstferien"), week.on(9).entries.map { it.entry.title })
        assertEquals(listOf("Herbstferien"), week.on(10).running)
        assertEquals(listOf("Herbstferien"), week.on(11).running)
        assertTrue(week.on(12).running.isEmpty())
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
        val week = WeekLayout.build(dates, SampleData.entries, today)
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
