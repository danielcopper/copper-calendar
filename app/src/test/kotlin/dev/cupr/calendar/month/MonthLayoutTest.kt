package dev.cupr.calendar.month

import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The month's display rules, each rule's test naming the dashboard's rule it
 * pins (the dashboard's file is named in PhoneMonth), the checks on days,
 * weeks and bars, and the sample month.
 */
class MonthLayoutTest {
    private val november = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 11)

    private fun date(
        month: Int,
        day: Int,
    ) = LocalDate.of(2026, month, day)

    private fun allDay(
        first: LocalDate,
        days: Long = 1,
        title: String = "Eintrag",
        kind: String? = null,
        drawing: String? = null,
    ) = Entry(
        start = first.atStartOfDay(),
        end = first.plusDays(days).atStartOfDay(),
        allDay = true,
        title = title,
        calendar = "calendar.family",
        kind = kind,
        drawing = drawing,
    )

    private fun timed(
        start: LocalDateTime,
        end: LocalDateTime,
        title: String = "Termin",
    ) = Entry(start = start, end = end, allDay = false, title = title, calendar = "calendar.family")

    private fun build(vararg entries: Entry) = MonthLayout.build(november, entries.toList(), today)

    private fun MonthView.day(date: LocalDate) = weeks.flatMap { it.days }.single { it.date == date }

    // calendar_grid: from the Monday on or before the 1st to the Sunday on or after the last.

    @Test
    fun `calendar_grid runs from the Monday before the first to the Sunday after the last`() {
        val grid = MonthLayout.grid(november)
        assertEquals(date(10, 26), grid.first())
        assertEquals(date(12, 6), grid.last())
        assertEquals(42, grid.size)
    }

    @Test
    fun `calendar_grid starts on the first when it is a Monday and ends on the last when it is a Sunday`() {
        val grid = MonthLayout.grid(YearMonth.of(2027, 2))
        assertEquals(LocalDate.of(2027, 2, 1), grid.first())
        assertEquals(LocalDate.of(2027, 2, 28), grid.last())
        assertEquals(4, MonthLayout.build(YearMonth.of(2027, 2), emptyList(), today).weeks.size)
    }

    // calendar_month_view: per-day flags: other month, past, today, weekend, holiday.

    @Test
    fun `calendar_month_view marks a day of another month faint and never past`() {
        val view = build()
        val october = view.day(date(10, 26))
        assertTrue(october.otherMonth)
        assertFalse(october.past)
        val december = view.day(date(12, 1))
        assertTrue(december.otherMonth)
        assertFalse(december.past)
    }

    @Test
    fun `calendar_month_view dims the days of the month before today only`() {
        val view = build()
        assertTrue(view.day(date(11, 1)).past)
        assertTrue(view.day(date(11, 10)).past)
        assertFalse(view.day(date(11, 11)).past)
        assertTrue(view.day(date(11, 11)).today)
        assertFalse(view.day(date(11, 12)).past)
    }

    @Test
    fun `calendar_days marks Saturday and Sunday as weekend`() {
        val week = build().weeks[2]
        assertEquals(listOf(false, false, false, false, false, true, true), week.days.map { it.weekend })
    }

    @Test
    fun `calendar_days marks a day with a public holiday so its number is red`() {
        val view = build(allDay(date(11, 18), kind = "holiday", title = "Buß- und Bettag"))
        assertTrue(view.day(date(11, 18)).holiday)
        assertFalse(view.day(date(11, 19)).holiday)
    }

    @Test
    fun `calendar_days reds only the first day of a public holiday over several days`() {
        val view = build(allDay(date(11, 18), days = 2, kind = "holiday", title = "Zwei Feiertage"))
        assertTrue(view.day(date(11, 18)).holiday)
        assertFalse(view.day(date(11, 19)).holiday)
    }

    // calendar_marked: a special day or public holiday of one day stands beside the number.

    @Test
    fun `calendar_marked puts a special day's drawing beside the number, not among the entries`() {
        val day = build(allDay(date(11, 11), kind = "special", drawing = "lantern.svg")).day(date(11, 11))
        assertEquals(listOf("lantern.svg"), day.marks.map { it.drawing })
        assertTrue(day.shown.isEmpty())
        assertEquals(0, day.more)
    }

    @Test
    fun `calendar_marked takes a public holiday of one day too, also without a drawing`() {
        val day = build(allDay(date(11, 18), kind = "holiday")).day(date(11, 18))
        assertTrue(day.shown.isEmpty())
        assertTrue(day.marks.isEmpty())
    }

    @Test
    fun `calendar_marked leaves a special day over several days to the bars`() {
        val view = build(allDay(date(11, 18), days = 2, kind = "special", drawing = "tree.svg"))
        assertTrue(view.day(date(11, 18)).marks.isEmpty())
        assertEquals(1, view.weeks[3].bars.size)
    }

    @Test
    fun `calendar_month_view shows one drawing at phone size`() {
        val day =
            build(
                allDay(date(11, 11), kind = "special", drawing = "lantern.svg", title = "A"),
                allDay(date(11, 11), kind = "special", drawing = "lantern.svg", title = "B"),
                allDay(date(11, 11), kind = "special", drawing = "candles-1.svg", title = "C"),
            ).day(date(11, 11))
        assertEquals(listOf("lantern.svg"), day.marks.map { it.drawing })
    }

    // calendar_month_view: at most 2 entries on the phone, of more one less and "+N".

    @Test
    fun `calendar_month_view shows two entries in full`() {
        val day = build(allDay(date(11, 12), title = "A"), allDay(date(11, 12), title = "B")).day(date(11, 12))
        assertEquals(listOf("A", "B"), day.shown.map { it.title })
        assertEquals(0, day.more)
    }

    @Test
    fun `calendar_month_view shows one entry and +N of three`() {
        val day =
            build(
                allDay(date(11, 12), title = "A"),
                allDay(date(11, 12), title = "B"),
                allDay(date(11, 12), title = "C"),
            ).day(date(11, 12))
        assertEquals(listOf("A"), day.shown.map { it.title })
        assertEquals(2, day.more)
    }

    @Test
    fun `calendar_days orders all-day entries first, then by time, then by title`() {
        val day =
            build(
                timed(date(11, 12).atTime(18, 0), date(11, 12).atTime(19, 0), "Spät"),
                timed(date(11, 12).atTime(8, 0), date(11, 12).atTime(9, 0), "Früh"),
                allDay(date(11, 12), title = "Ganztags"),
            ).day(date(11, 12))
        assertEquals(listOf("Ganztags"), day.shown.map { it.title })
        assertEquals(2, day.more)
        val twoTimed =
            build(
                timed(date(11, 12).atTime(9, 0), date(11, 12).atTime(10, 0), "Zweiter"),
                timed(date(11, 12).atTime(9, 0), date(11, 12).atTime(10, 0), "Äpfel"),
            ).day(date(11, 12))
        assertEquals(listOf("Äpfel", "Zweiter"), twoTimed.shown.map { it.title })
    }

    @Test
    fun `calendar_days orders timed entries by their time, not their title`() {
        val day =
            build(
                // The earlier start has the later end, so only the start orders them so.
                timed(date(11, 12).atTime(18, 0), date(11, 12).atTime(19, 0), "Abendessen"),
                timed(date(11, 12).atTime(8, 0), date(11, 12).atTime(20, 0), "Zahnarzt"),
            ).day(date(11, 12))
        assertEquals(listOf("Zahnarzt", "Abendessen"), day.shown.map { it.title })
    }

    // calendar_days: which entries stand in the grid at all.

    @Test
    fun `a timed entry of no length at the grid's first midnight stands on the first day`() {
        val midnight = date(10, 26).atStartOfDay()
        val view = build(timed(midnight, midnight, "Mitternacht"))
        assertEquals(listOf("Mitternacht"), view.day(date(10, 26)).shown.map { it.title })
    }

    @Test
    fun `calendar_days leaves out an entry that ended before the grid or begins after it`() {
        val view =
            build(
                allDay(date(10, 25), title = "Davor"),
                allDay(date(12, 7), title = "Danach"),
                allDay(date(10, 26), title = "Erster Tag"),
                allDay(date(12, 6), title = "Letzter Tag"),
            )
        val titles = view.weeks.flatMap { week -> week.days.flatMap { day -> day.shown.map { it.title } } }
        assertEquals(listOf("Erster Tag", "Letzter Tag"), titles)
    }

    @Test
    fun `calendar_days treats an all-day entry's end as the midnight after its last day`() {
        val view = build(allDay(date(11, 12), days = 1, title = "Ein Tag"))
        assertEquals(listOf("Ein Tag"), view.day(date(11, 12)).shown.map { it.title })
        assertTrue(view.weeks.all { it.bars.isEmpty() })
    }

    @Test
    fun `calendar_days makes a timed entry over midnight a bar`() {
        val view = build(timed(date(11, 13).atTime(22, 0), date(11, 14).atTime(2, 0), "Nachtfahrt"))
        assertTrue(view.day(date(11, 13)).shown.isEmpty())
        val bar = view.weeks[2].bars.single()
        assertEquals(4 to 5, bar.from to bar.to)
    }

    // calendar_month_view: entries over several days as bars in lanes.

    @Test
    fun `calendar_month_view draws a bar inside one week rounded at both ends with its title`() {
        val bar = build(allDay(date(11, 20), days = 3, title = "Urlaub")).weeks[3].bars.single()
        assertEquals(4, bar.from)
        assertEquals(6, bar.to)
        assertEquals(0, bar.lane)
        assertTrue(bar.opens)
        assertTrue(bar.closes)
        assertTrue(bar.titled)
    }

    @Test
    fun `calendar_month_view repeats a bar's title at the start of each further week`() {
        val view = build(allDay(date(11, 13), days = 5, title = "Reise"))
        val first = view.weeks[2].bars.single()
        assertEquals(4 to 6, first.from to first.to)
        assertTrue(first.opens)
        assertFalse(first.closes)
        assertTrue(first.titled)
        val second = view.weeks[3].bars.single()
        assertEquals(0 to 1, second.from to second.to)
        assertFalse(second.opens)
        assertTrue(second.closes)
        assertTrue(second.titled)
    }

    @Test
    fun `calendar_month_view opens no bar that began before the grid and closes none that ends after it`() {
        val view =
            build(
                allDay(date(10, 20), days = 9, title = "Herbstferien", kind = "holidays"),
                allDay(date(12, 5), days = 3, title = "Winter"),
            )
        val holidays =
            view.weeks
                .first()
                .bars
                .single()
        assertEquals(0 to 2, holidays.from to holidays.to)
        assertFalse(holidays.opens)
        assertTrue(holidays.closes)
        assertTrue(holidays.titled)
        val winter =
            view.weeks
                .last()
                .bars
                .single()
        assertEquals(5 to 6, winter.from to winter.to)
        assertTrue(winter.opens)
        assertFalse(winter.closes)
    }

    @Test
    fun `calendar_month_view puts overlapping bars in lanes, the longer first, and reuses a free lane`() {
        val week =
            build(
                allDay(date(11, 16), days = 2, title = "Kurz"),
                allDay(date(11, 16), days = 4, title = "Lang"),
                allDay(date(11, 21), days = 2, title = "Später"),
                allDay(date(11, 19), days = 2, title = "Anschluss"),
            ).weeks[3]
        val lanes = week.bars.associate { it.entry.title to it.lane }
        assertEquals(
            mapOf("Lang" to 0, "Kurz" to 1, "Später" to 0, "Anschluss" to 1).toSortedMap(),
            lanes.toSortedMap(),
        )
        assertEquals(2, week.lanes)
    }

    @Test
    fun `calendar_month_view starts a bar on the column another ends on in a new lane`() {
        val week =
            build(
                allDay(date(11, 16), days = 3, title = "Erste"),
                allDay(date(11, 18), days = 3, title = "Zweite"),
            ).weeks[3]
        assertEquals(listOf(0, 1), week.bars.map { it.lane })
    }

    @Test
    fun `calendar_month_view keeps bars of the same span in their days' order`() {
        val week =
            build(
                allDay(date(11, 16), days = 3, title = "Beta"),
                allDay(date(11, 16), days = 3, title = "Alpha"),
            ).weeks[3]
        assertEquals(listOf("Alpha" to 0, "Beta" to 1), week.bars.map { it.entry.title to it.lane })
    }

    @Test
    fun `a week has seven days and a bar stays inside its week`() {
        val day = build().weeks[0].days[0]
        assertThrows(IllegalArgumentException::class.java) { Week(List(6) { day }, emptyList()) }
        val entry = allDay(date(11, 16), days = 2)
        assertThrows(IllegalArgumentException::class.java) {
            Bar(entry, from = 5, to = 7, lane = 0, opens = true, closes = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bar(entry, from = 3, to = 2, lane = 0, opens = true, closes = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bar(entry, from = 0, to = 1, lane = -1, opens = true, closes = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Bar(entry, from = -1, to = 1, lane = 0, opens = true, closes = true)
        }
    }

    @Test
    fun `a past day is neither today nor of another month, and +N counts no fewer than none`() {
        val day = build().weeks[2].days[0]
        assertThrows(IllegalArgumentException::class.java) { day.copy(past = true, today = true) }
        assertThrows(IllegalArgumentException::class.java) { day.copy(past = true, otherMonth = true) }
        assertThrows(IllegalArgumentException::class.java) { day.copy(more = -1) }
    }

    // The sample month: its weeks, bars and cells.

    @Test
    fun `the sample month places its entries in six weeks, two bars and the cells`() {
        val view = MonthLayout.build(SampleData.month, SampleData.entries, LocalDate.now(SampleData.clock))
        assertEquals(6, view.weeks.size)
        assertEquals(2, view.todayWeek)
        assertEquals(listOf(0, 0, 0, 1, 1, 0), view.weeks.map { it.lanes })
        val holiday = view.weeks[3].bars.single()
        assertEquals("Kurzurlaub Ostsee" to (4 to 6), holiday.entry.title to (holiday.from to holiday.to))
        val trip = view.weeks[4].bars.single()
        assertEquals("Dienstreise Hamburg" to (3 to 4), trip.entry.title to (trip.from to trip.to))
        val advent = view.day(date(11, 28))
        assertEquals(listOf("Adventskranz binden"), advent.shown.map { it.title })
        assertEquals(2, advent.more)
        val today = view.day(date(11, 11))
        assertEquals(listOf("lantern.svg"), today.marks.map { it.drawing })
        assertEquals(listOf("Gelber Sack", "Martinsumzug Kita"), today.shown.map { it.title })
        assertEquals(listOf("halloween.svg"), view.day(date(10, 31)).marks.map { it.drawing })
        assertEquals(listOf("Schwimmkurs Anna"), view.day(date(10, 26)).shown.map { it.title })
    }
}
