package dev.cupr.calendar.month

import dev.cupr.calendar.days.CalendarDays
import dev.cupr.calendar.days.CalendarDays.Placed
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.days.isWeekend
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * The month as the dashboard's calendar page shows it at phone size, one
 * [Week] per row: its rules `calendar_grid`, `calendar_days`,
 * `calendar_marked` and `calendar_month_view` (the dashboard's file is named
 * in [dev.cupr.calendar.render.PhoneMonth]). Only the display rules are
 * ported; an entry's kind, drawing and colour come with the entry (D4).
 * [MonthLayout] makes it, and the types below it.
 */
@ConsistentCopyVisibility
data class MonthView internal constructor(
    val month: YearMonth,
    val weeks: List<Week>,
) {
    /** The row of the week holding today, else the first. */
    val todayWeek: Int get() = weeks.indexOfFirst { week -> week.days.any { it.today } }.coerceAtLeast(0)
}

@ConsistentCopyVisibility
data class Week internal constructor(
    /** Its seven days in order: Monday to Sunday in the month, from any day on in "Woche quer". */
    val days: List<Day>,
    /** The entries over several days that cross this week, as bars. */
    val bars: List<Bar>,
) {
    init {
        require(days.size == DAYS_IN_WEEK) { "A week has $DAYS_IN_WEEK days, not ${days.size}" }
    }

    /** How many lanes the bars take; the cells' own entries start below them. */
    val lanes: Int get() = bars.maxOfOrNull { it.lane + 1 } ?: 0
}

@ConsistentCopyVisibility
data class Day internal constructor(
    val date: LocalDate,
    /** A day of the month before or after, drawn faint. */
    val otherMonth: Boolean,
    /** A day of the month before today, dimmed; never set on a day of another month. */
    val past: Boolean,
    val today: Boolean,
    /** A public holiday starts on it: its number is red. */
    val holiday: Boolean,
    /**
     * Its marked entries beside its number: special days and public holidays
     * with a drawing, then special days without one, which draw their icon or
     * a dot (D24).
     */
    val marks: List<Entry>,
    /**
     * Its own entries shown in the cell, in order; in "Woche quer" every one,
     * the marks with no room beside the number among them.
     */
    val shown: List<Entry>,
    /**
     * How many of its entries "+N" counts, 0 for none: in the month its own
     * not shown and the marks with no room (D24); in "Woche quer" only those
     * a full week leaves out for the bitmap limit.
     */
    val more: Int,
) {
    init {
        require(!(past && (today || otherMonth))) { "A past day is neither today nor of another month: $date" }
        require(more >= 0) { "A day's \"+N\" counts $more" }
    }

    val weekend: Boolean get() = date.isWeekend
}

/** An entry over several days, as one week draws it. */
@ConsistentCopyVisibility
data class Bar internal constructor(
    val entry: Entry,
    /** The first and last column it covers in this week (0 = the week's first day). */
    val from: Int,
    val to: Int,
    val lane: Int,
    /** It begins in this week: rounded start. */
    val opens: Boolean,
    /** It ends in this week: rounded end. */
    val closes: Boolean,
) {
    init {
        require(from in 0..to && to < DAYS_IN_WEEK) { "A bar covers columns $from to $to of a week" }
        require(lane >= 0) { "A bar's lane is $lane" }
    }

    /** Its title shows: where it begins and at the start of each week. */
    val titled: Boolean get() = opens || from == 0
}

object MonthLayout {
    /** At phone size a cell shows at most 2 entries, or 1 and "+N" (`most`). */
    const val MOST_ENTRIES = 2

    /** At phone size a cell shows 1 drawing of its marked entries (`drawn`). */
    const val MOST_DRAWINGS = 1

    /** The dates from the Monday on or before the 1st to the Sunday on or after the last day (`calendar_grid`). */
    fun grid(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    fun build(
        month: YearMonth,
        entries: List<Entry>,
        today: LocalDate,
    ): MonthView = MonthView(month, weeks(grid(month), entries, today, month, every = false))

    /**
     * Seven days from any day on drawn as one week of the month ("Woche
     * quer", D19): no day of another month, and every entry of a day shown
     * instead of [MOST_ENTRIES] and "+N"; a marked entry with no room beside
     * the number is shown as a line among them.
     */
    fun week(
        dates: List<LocalDate>,
        entries: List<Entry>,
        today: LocalDate,
    ): Week {
        require(dates.size == DAYS_IN_WEEK) { "A week has $DAYS_IN_WEEK days, not ${dates.size}" }
        CalendarDays.requireRun(dates)
        return weeks(dates, entries, today, month = null, every = true).single()
    }

    /** The weeks of dates; month: the days of any other month are drawn faint; every: no limit of entries. */
    private fun weeks(
        dates: List<LocalDate>,
        entries: List<Entry>,
        today: LocalDate,
        month: YearMonth?,
        every: Boolean,
    ): List<Week> {
        // The month keeps a timed entry of no length at the grid's first midnight, as the dashboard's grid does.
        val placed = CalendarDays.onDays(dates, entries, keepFirstMidnight = true)
        val days =
            dates.mapIndexed { index, date ->
                val otherMonth = month != null && YearMonth.from(date) != month
                day(date, otherMonth, today, placed[index], every)
            }
        val bars =
            placed.flatMapIndexed { index, rows ->
                rows.filter { it.multi }.map { row ->
                    val to = dates.indexOf(row.last).takeIf { it >= 0 } ?: dates.lastIndex
                    Span(row, index, to)
                }
            }
        return dates.indices.step(DAYS_IN_WEEK).map { start ->
            Week(days.subList(start, start + DAYS_IN_WEEK).toList(), lanes(bars, dates, start))
        }
    }

    private data class Span(
        val row: Placed,
        val from: Int,
        val to: Int,
    )

    /** A special day or a public holiday of one day stands beside the number (`calendar_marked`). */
    private fun marked(row: Placed) = row.entry.kind in setOf(Kinds.SPECIAL, Kinds.HOLIDAY) && !row.multi

    /**
     * The marked entries that stand beside a day's number (D24): those with a
     * drawing first, as the dashboard draws them, then special days without
     * one, which the dashboard leaves out but the widget draws as its icon or
     * a dot; each group in the day's order. A public holiday without a drawing
     * shows only as its red number. The day draws the first [MOST_DRAWINGS];
     * in the month "+N" counts the rest, in "Woche quer" they are lines among
     * the entries.
     */
    private fun marks(rows: List<Placed>): List<Placed> =
        rows
            .filter(::marked)
            .filter { it.entry.drawing != null || it.entry.kind == Kinds.SPECIAL }
            .sortedBy { it.entry.drawing == null }

    /**
     * A day's cell. `holiday` sees only the entries placed on the day, so a
     * public holiday over several days reds its first day, as on the dashboard.
     * every: all of its entries shown, the marks with no room beside the
     * number among them in the day's order, and no "+N".
     */
    private fun day(
        date: LocalDate,
        otherMonth: Boolean,
        today: LocalDate,
        rows: List<Placed>,
        every: Boolean,
    ): Day {
        val marks = marks(rows)
        val beside = marks.take(MOST_DRAWINGS)
        val shown: List<Entry>
        val more: Int
        if (every) {
            val lines = marks.drop(MOST_DRAWINGS)
            shown = rows.filter { row -> !row.multi && (!marked(row) || lines.any { it === row }) }.map { it.entry }
            more = 0
        } else {
            val singles = rows.filter { !it.multi && !marked(it) }.map { it.entry }
            // Marks with no room beside the number go into "+N", so at most one of the day's own entries is shown.
            val overflow = marks.size - beside.size
            shown = if (singles.size <= MOST_ENTRIES && overflow == 0) singles else singles.take(MOST_ENTRIES - 1)
            more = singles.size - shown.size + overflow
        }
        return Day(
            date = date,
            otherMonth = otherMonth,
            past = !otherMonth && date.isBefore(today),
            today = date == today,
            holiday = rows.any { it.entry.kind == Kinds.HOLIDAY },
            marks = beside.map { it.entry },
            shown = shown,
            more = more,
        )
    }

    /**
     * The bars crossing the week from [start], by first day and then the
     * longer first (bars of the same span keep their days' order), each in
     * the first lane free since its first column.
     */
    private fun lanes(
        spans: List<Span>,
        dates: List<LocalDate>,
        start: Int,
    ): List<Bar> {
        val end = start + DAYS_IN_WEEK - 1
        val ends = mutableListOf<Int>()
        return spans
            .filter { it.from <= end && it.to >= start }
            .sortedWith(compareBy<Span> { it.from }.thenByDescending { it.to - it.from })
            .map { span ->
                val from = maxOf(span.from, start) - start
                val to = minOf(span.to, end) - start
                var lane = ends.indexOfFirst { it < from }
                if (lane < 0) {
                    ends.add(0)
                    lane = ends.lastIndex
                }
                ends[lane] = to
                val opens = span.row.first == dates[span.from] && span.from >= start
                val closes = span.row.last == dates[span.to] && span.to <= end
                Bar(span.row.entry, from, to, lane, opens, closes)
            }
    }
}
