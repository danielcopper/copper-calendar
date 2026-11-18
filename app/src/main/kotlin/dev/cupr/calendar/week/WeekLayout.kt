package dev.cupr.calendar.week

import androidx.annotation.StringRes
import dev.cupr.calendar.R
import dev.cupr.calendar.days.CLOCK_TIME
import dev.cupr.calendar.days.CalendarDays
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.days.DAY_AND_MONTH
import dev.cupr.calendar.days.isWeekend
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** The days a week widget shows, set per widget (D20), and its header's title. */
enum class WeekDays(
    @param:StringRes val title: Int,
) {
    /** Monday to Sunday of the current week, the default. */
    MONDAY_TO_SUNDAY(R.string.week_this_week),

    /** Today and the six days after it. */
    FROM_TODAY(R.string.week_next_days),
    ;

    /** The seven dates, in order. */
    fun dates(today: LocalDate): List<LocalDate> {
        val first =
            when (this) {
                MONDAY_TO_SUNDAY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                FROM_TODAY -> today
            }
        return List(DAYS_IN_WEEK) { first.plusDays(it.toLong()) }
    }
}

/**
 * A day of the week's rows ("Woche"), as the dashboard's tile shows it
 * (`calendar_days` with `empty`, `past` and `runs`, and `calendar_lines`;
 * the dashboard's file is named in [dev.cupr.calendar.render.PhoneMonth]).
 * [WeekLayout] makes it.
 */
@ConsistentCopyVisibility
data class WeekDay internal constructor(
    val date: LocalDate,
    val today: Boolean,
    /** Before today: the whole row is dimmed. */
    val past: Boolean,
    /** Its entries' rows, in order: an entry over several days has one on each day it covers (D61). */
    val entries: List<DayEntry>,
    /**
     * How many of its last entries the row leaves out and counts in a
     * "+N weitere" line instead, 0 for none: only a full week does, for the
     * bitmap limit.
     */
    val more: Int = 0,
) {
    init {
        require(!(past && today)) { "A past day is not today: $date" }
        require(more in 0..entries.size) { "A day of ${entries.size} entries cannot leave out $more" }
    }

    /** A public holiday stands on it, not one continued from an earlier day: its number is red. */
    val holiday: Boolean get() = entries.any { !it.continued && it.entry.kind == Kinds.HOLIDAY }

    val weekend: Boolean get() = date.isWeekend

    /** The entries the row draws: all but the [more] it leaves out. */
    val shown: List<DayEntry> get() = entries.dropLast(more)

    /** The day leaving out one more of its last entries; it has one left to show. */
    internal fun leaveOutLast(): WeekDay {
        check(shown.isNotEmpty()) { "$date shows no entry to leave out" }
        return copy(more = more + 1)
    }
}

/** An entry's row on a day of the week. [WeekLayout] makes it. */
@ConsistentCopyVisibility
data class DayEntry internal constructor(
    val entry: Entry,
    /**
     * What the row says quietly at its right end: on every row of a timed
     * entry its time there, on an all-day row only the end of an entry that
     * runs on after the last day shown.
     */
    val time: RowTime?,
    /** Its short addition after the title; none for no addition. */
    val addition: Addition?,
    /** It stands on an earlier day shown: a further day of an entry over several days. */
    val continued: Boolean,
    /** A timed entry of today whose end has passed: its row is dimmed. */
    val over: Boolean,
) {
    /** A timed entry's row, drawn as one; else an all-day row. */
    val timed: Boolean get() = !entry.allDay

    init {
        if (timed) {
            require(time != null) { "A timed row has its time: ${entry.title}" }
        } else {
            require(time == null || (time is RowTime.Until && time.end is End.Later && time.end.time == null)) {
                "An all-day row's only time is the day its entry ends on, after the last day shown: ${entry.title}"
            }
            require(!over) { "An all-day entry is never over: ${entry.title}" }
        }
    }
}

/** An entry's short addition after its title, worded by the app while drawing (D18). */
sealed interface Addition {
    /** A public holiday's "Feiertag". */
    data object Holiday : Addition

    /** A birthday's "wird 26". */
    data class Turns(
        val age: Int,
    ) : Addition {
        init {
            require(age >= 0) { "A birthday turns $age" }
        }
    }
}

/** What a row says at its right end, worded by the app while drawing (D61). */
sealed interface RowTime {
    /** A timed entry within its day: its start and its end, "12:30–13:30"; its start alone when it has no length. */
    data class Within(
        val start: String,
        val end: String?,
    ) : RowTime

    /** A timed entry over several days on its first day: "ab 09:00". */
    data class From(
        val start: String,
    ) : RowTime

    /** A timed entry over several days on a day between its first and last: "Tag 2 von 3". */
    data class DayOf(
        val day: Int,
        val days: Int,
    ) : RowTime {
        init {
            require(day in 2 until days) { "Day $day of $days lies not between the first and the last" }
        }
    }

    /** Where an entry over several days ends: "bis 16:00" on its last day, "bis Di" on the last day shown before it. */
    data class Until(
        val end: End,
    ) : RowTime

    /** A timed entry that begins on the last day shown and ends after it: "08:00 – Di, 18:00". */
    data class FromTo(
        val start: String,
        val end: End.Later,
    ) : RowTime
}

/** Where an entry over several days ends, as one of its rows names it. */
sealed interface End {
    /** On the row's own day: its time, "24:00" for the midnight after it. */
    data class At(
        val time: String,
    ) : End

    /**
     * On a later day: its weekday when it lies at most six days after the
     * row's day, else its date; and its time, none when it ends at midnight.
     */
    data class Later(
        /** The entry's last day: for an end at midnight, the day before. */
        val day: LocalDate,
        val weekday: Boolean,
        val time: String?,
    ) : End
}

/**
 * The days of the week's rows from the entries, after the dashboard's tile
 * (`calendar_days`, which places the entries as [CalendarDays] does for the
 * month, and `calendar_lines`). Every day shows, also an empty one; past
 * entries stay. An entry has a row on its first day, one that began before
 * the first day on that, and an entry over several days one on each further
 * day it covers, which says what that day needs (D61). A timed entry of
 * today whose end has passed is over.
 */
object WeekLayout {
    /** The days of dates, which run on without a gap, as they stand at now. */
    fun build(
        dates: List<LocalDate>,
        entries: List<Entry>,
        now: LocalDateTime,
    ): List<WeekDay> {
        val today = now.toLocalDate()
        val rows = dates.associateWith { mutableListOf<DayEntry>() }
        for (placed in CalendarDays.place(dates, entries, keepFirstMidnight = false)) {
            for (date in dates.filter { it == placed.date || (it.isAfter(placed.date) && !it.isAfter(placed.last)) }) {
                rows.getValue(date) += row(placed, date, dates.last(), now)
            }
        }
        return dates.map { date ->
            WeekDay(
                date = date,
                today = date == today,
                past = date.isBefore(today),
                entries = ordered(rows.getValue(date)),
            )
        }
    }

    /**
     * The header's range of the days from first to last, with Java's German
     * short month names: "9.–15. Nov.", or "30. Nov. – 6. Dez." across months.
     */
    fun range(
        first: LocalDate,
        last: LocalDate,
    ): String {
        val end = last.format(DAY_AND_MONTH)
        return if (YearMonth.from(first) == YearMonth.from(last)) {
            "${first.dayOfMonth}.–$end"
        } else {
            "${first.format(DAY_AND_MONTH)} – $end"
        }
    }

    /**
     * A day's rows in the tile's order: the all-day rows, those continued
     * from an earlier day before the day's own, which go by title; then the
     * timed rows, again the continued ones before the day's own, each by
     * start, then title.
     */
    private fun ordered(rows: List<DayEntry>): List<DayEntry> {
        val byTitle = compareBy(CalendarDays.byTitle) { row: DayEntry -> row.entry }
        val byStart = compareBy(CalendarDays.byStartThenTitle) { row: DayEntry -> row.entry }
        val (timed, allDay) = rows.partition { it.timed }
        return allDay.filter { it.continued } +
            allDay.filterNot { it.continued }.sortedWith(byTitle) +
            timed.filter { it.continued }.sortedWith(byStart) +
            timed.filterNot { it.continued }.sortedWith(byStart)
    }

    /** An entry's row on date, one of the days it covers; lastShown is the last day shown. */
    private fun row(
        placed: CalendarDays.Placed,
        date: LocalDate,
        lastShown: LocalDate,
        now: LocalDateTime,
    ): DayEntry {
        val entry = placed.entry
        // The last row shown of an entry that runs on after it names its end.
        val latest = date == lastShown && placed.last.isAfter(date)
        val time =
            when {
                !entry.allDay -> timeOf(placed, date, latest)
                latest -> RowTime.Until(later(placed, date))
                else -> null
            }
        return DayEntry(
            entry = entry,
            time = time,
            addition = if (placed.multi) null else additionOf(entry),
            continued = date != placed.date,
            over = !entry.allDay && date == now.toLocalDate() && !entry.end.isAfter(now),
        )
    }

    /** A timed entry's time on date; latest: its last row shown, before its last day. */
    private fun timeOf(
        placed: CalendarDays.Placed,
        date: LocalDate,
        latest: Boolean,
    ): RowTime {
        val entry = placed.entry
        val start = entry.start.format(CLOCK_TIME)
        return when {
            !placed.multi -> RowTime.Within(start, endOfDay(entry.end, date).takeIf { entry.end.isAfter(entry.start) })
            latest && placed.first == date -> RowTime.FromTo(start, later(placed, date))
            latest -> RowTime.Until(later(placed, date))
            placed.first == date -> RowTime.From(start)
            placed.last == date -> RowTime.Until(End.At(endOfDay(entry.end, date)))
            else -> RowTime.DayOf(dayNumber(placed.first, date), dayNumber(placed.first, placed.last))
        }
    }

    /** An entry's end as named from date, an earlier day than its last. */
    private fun later(
        placed: CalendarDays.Placed,
        date: LocalDate,
    ): End.Later {
        val end = placed.entry.end
        val time = end.format(CLOCK_TIME).takeIf { !placed.entry.allDay && end.toLocalDate() == placed.last }
        return End.Later(placed.last, ChronoUnit.DAYS.between(date, placed.last) <= WEEKDAY_DAYS, time)
    }

    /** A public holiday's "Feiertag", a birthday's age; none for any other entry. */
    private fun additionOf(entry: Entry): Addition? {
        // A year of birth after the entry's year is wrong data; it gets no age.
        val age = entry.year?.let { entry.start.year - it }?.takeIf { it >= 0 }
        return when {
            entry.kind == Kinds.HOLIDAY -> Addition.Holiday
            entry.kind == Kinds.BIRTHDAY && age != null -> Addition.Turns(age)
            else -> null
        }
    }

    /** Which of an entry's days date is, counted from its first day as 1. */
    private fun dayNumber(
        first: LocalDate,
        date: LocalDate,
    ): Int = ChronoUnit.DAYS.between(first, date).toInt() + 1

    /** A time that ends on day, "24:00" for the midnight after it. */
    private fun endOfDay(
        end: LocalDateTime,
        day: LocalDate,
    ): String = if (end.toLocalDate() == day) end.format(CLOCK_TIME) else MIDNIGHT

    private const val MIDNIGHT = "24:00"

    /** An end at most this many days after a row's day is named by its weekday, a later one by its date. */
    private const val WEEKDAY_DAYS = 6
}
