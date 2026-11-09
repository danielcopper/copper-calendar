package dev.cupr.calendar.month

import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import java.text.Collator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** The days of a week, Monday to Sunday: the month's columns. */
const val DAYS_IN_WEEK = 7

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
    /** Monday to Sunday. */
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
    /** Its marked entries with a drawing (special days, public holidays), drawn beside its number. */
    val marks: List<Entry>,
    /** Its own entries shown in the cell, in order. */
    val shown: List<Entry>,
    /** How many of its own entries "+N" counts, 0 for none. */
    val more: Int,
) {
    val weekend: Boolean get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}

/** An entry over several days, as one week draws it. */
@ConsistentCopyVisibility
data class Bar internal constructor(
    val entry: Entry,
    /** The first and last column it covers in this week (0 = Monday). */
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

    private val collator: Collator = Collator.getInstance(Locale.GERMAN)

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
    ): MonthView {
        val dates = grid(month)
        val placed = place(dates, entries)
        val days =
            dates.mapIndexed { index, date -> day(date, month, today, placed[index]) }
        val bars =
            placed.flatMapIndexed { index, rows ->
                rows.filter { it.multi }.map { row ->
                    val to = dates.indexOf(row.last).takeIf { it >= 0 } ?: dates.lastIndex
                    Span(row, index, to)
                }
            }
        val weeks =
            dates.indices.step(DAYS_IN_WEEK).map { start ->
                Week(days.subList(start, start + DAYS_IN_WEEK).toList(), lanes(bars, dates, start))
            }
        return MonthView(month, weeks)
    }

    /** An entry as placed on a day of the grid (`calendar_days`' row). */
    private data class Row(
        val entry: Entry,
        val first: LocalDate,
        val last: LocalDate,
        val multi: Boolean,
        /** Timed and placed on its own first day, so ordered by its time. */
        val timed: Boolean,
    )

    private data class Span(
        val row: Row,
        val from: Int,
        val to: Int,
    )

    /**
     * Each entry stands on its first day, one that began before the grid on
     * its first date; one that ended before the grid or begins after it is
     * left out (`calendar_days` with `empty` and `past`). On each day the
     * all-day entries first, then by time, then by title.
     */
    private fun place(
        dates: List<LocalDate>,
        entries: List<Entry>,
    ): List<List<Row>> {
        val begin = dates.first()
        // An entry that begins after the grid lands on a date the grid never reads.
        val placed =
            entries
                .filter { it.end.isAfter(begin.atStartOfDay()) }
                .groupBy({ entry -> maxOf(entry.start.toLocalDate(), begin) }) { entry ->
                    val first = entry.start.toLocalDate()
                    val last = entry.end.minusNanos(1).toLocalDate()
                    // Timed and standing on its own first day, not carried onto the grid's first date.
                    val timed = !entry.allDay && !first.isBefore(begin)
                    Row(entry, first, last, multi = last.isAfter(first), timed = timed)
                }
        val order =
            compareBy<Row>({ it.timed }, { if (it.timed) it.entry.start else null })
                .then { a, b -> collator.compare(a.entry.title, b.entry.title) }
        return dates.map { date -> placed[date].orEmpty().sortedWith(order) }
    }

    /** A special day or a public holiday of one day stands beside the number (`calendar_marked`). */
    private fun marked(row: Row) = row.entry.kind in setOf(Kinds.SPECIAL, Kinds.HOLIDAY) && !row.multi

    /**
     * A day's cell. `holiday` sees only the entries placed on the day, so a
     * public holiday over several days reds its first day, as on the dashboard.
     */
    private fun day(
        date: LocalDate,
        month: YearMonth,
        today: LocalDate,
        rows: List<Row>,
    ): Day {
        val marks = rows.filter(::marked)
        val singles = rows.filter { !it.multi && !marked(it) }.map { it.entry }
        val shown = if (singles.size <= MOST_ENTRIES) singles else singles.take(MOST_ENTRIES - 1)
        val otherMonth = YearMonth.from(date) != month
        return Day(
            date = date,
            otherMonth = otherMonth,
            past = !otherMonth && date.isBefore(today),
            today = date == today,
            holiday = rows.any { it.entry.kind == Kinds.HOLIDAY },
            marks = marks.filter { it.entry.drawing != null }.map { it.entry }.take(MOST_DRAWINGS),
            shown = shown,
            more = singles.size - shown.size,
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
