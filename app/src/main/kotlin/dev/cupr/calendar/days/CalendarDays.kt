package dev.cupr.calendar.days

import dev.cupr.calendar.model.Entry
import java.text.Collator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The days of a week, from whichever day it starts on. */
const val DAYS_IN_WEEK = 7

/** Saturday or Sunday. */
val LocalDate.isWeekend: Boolean get() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

/**
 * The day's long German name, "Mittwoch, 11. November", from Java's German
 * locale: a day's description for accessibility, and the last day an entry's
 * "bis" names (D18, D25).
 */
val LocalDate.longName: String get() = format(LONG_NAME)

private val LONG_NAME = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/**
 * The dashboard's rule `calendar_days` as the month and the week share it
 * (the dashboard's file is named in [dev.cupr.calendar.render.PhoneMonth]):
 * which entries a run of days shows, the day each stands on, and their order
 * there.
 */
internal object CalendarDays {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN)

    /**
     * On each day, the all-day entries and those carried onto the first date
     * first, then the timed ones by time, then by title.
     */
    private val order: Comparator<Placed> =
        compareBy<Placed>({ it.timed }, { if (it.timed) it.entry.start else null })
            .then { a, b -> collator.compare(a.entry.title, b.entry.title) }

    /** An entry on the day it stands on: its first day, or the first date for one that began before it. */
    class Placed(
        val entry: Entry,
        val date: LocalDate,
    ) {
        val first: LocalDate = entry.start.toLocalDate()

        init {
            require(!date.isBefore(first)) { "${entry.title} stands on $date, before its first day $first" }
        }

        /** Its last day: the day of the last moment before its end. */
        val last: LocalDate = entry.end.minusNanos(1).toLocalDate()

        val multi: Boolean get() = last.isAfter(first)

        /** Timed and standing on its own first day, so ordered by its time. */
        val timed: Boolean get() = !entry.allDay && first == date
    }

    /**
     * The entries the dates show, each on its day, per date in the order
     * above; dates run on without a gap. Left out: an entry that ended before
     * the first date or begins after the last. keepFirstMidnight keeps a
     * timed entry of no length at the first date's midnight, which ended
     * before it; the month keeps it, the week does not.
     */
    fun onDays(
        dates: List<LocalDate>,
        entries: List<Entry>,
        keepFirstMidnight: Boolean,
    ): List<List<Placed>> = byDay(dates, place(dates, entries, keepFirstMidnight))

    /** The entries the dates show, in the order given, each on its day (see [onDays]). */
    fun place(
        dates: List<LocalDate>,
        entries: List<Entry>,
        keepFirstMidnight: Boolean,
    ): List<Placed> {
        requireRun(dates)
        val begin = dates.first()
        val midnight = begin.atStartOfDay()
        return entries
            .filter { it.end.isAfter(midnight) || (keepFirstMidnight && it.start == midnight) }
            .filter { !it.start.toLocalDate().isAfter(dates.last()) }
            .map { Placed(it, maxOf(it.start.toLocalDate(), begin)) }
    }

    /** The entries [place] placed for the same dates, per date, in the order above. */
    fun byDay(
        dates: List<LocalDate>,
        placed: List<Placed>,
    ): List<List<Placed>> {
        val days = placed.groupBy { it.date }
        return dates.map { days[it].orEmpty().sortedWith(order) }
    }

    /** Dates are at least one, ascending, one day apart. */
    private fun requireRun(dates: List<LocalDate>) {
        require(dates.isNotEmpty()) { "A run of days has at least one day" }
        require(dates.zipWithNext().all { (a, b) -> b == a.plusDays(1) }) { "The days run on without a gap: $dates" }
    }
}
