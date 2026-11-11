package dev.cupr.calendar.week

import androidx.annotation.StringRes
import dev.cupr.calendar.R
import dev.cupr.calendar.days.CalendarDays
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.days.isWeekend
import dev.cupr.calendar.days.longName
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
 * (`calendar_days` with `empty`, `past` and `runs`; the dashboard's file is
 * named in [dev.cupr.calendar.render.PhoneMonth]). [WeekLayout] makes it.
 */
@ConsistentCopyVisibility
data class WeekDay internal constructor(
    val date: LocalDate,
    val today: Boolean,
    /** Before today: the whole row is dimmed. */
    val past: Boolean,
    /** Its entries, in order. */
    val entries: List<DayEntry>,
    /** The titles of the entries over several days that it lies in after their first day shown. */
    val running: List<String>,
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

    /** A public holiday stands on it: its number is red. */
    val holiday: Boolean get() = entries.any { it.entry.kind == Kinds.HOLIDAY }

    val weekend: Boolean get() = date.isWeekend

    /** The entries the row draws: all but the [more] it leaves out. */
    val shown: List<DayEntry> get() = entries.dropLast(more)

    /** The day leaving out one more of its last entries; it has one left to show. */
    internal fun leaveOutLast(): WeekDay {
        check(shown.isNotEmpty()) { "$date shows no entry to leave out" }
        return copy(more = more + 1)
    }
}

/** An entry as a row of the week shows it. [WeekLayout] makes it. */
@ConsistentCopyVisibility
data class DayEntry internal constructor(
    val entry: Entry,
    /** Timed within this day: its time ("16:00–16:45"); none for an entry drawn as a chip. */
    val time: String?,
    /** Its short addition after the title; none for no addition. */
    val addition: Addition?,
) {
    init {
        require(time == null || !entry.allDay) { "An all-day entry has no time: ${entry.title}" }
    }
}

/** An entry's short addition, worded by the app while drawing (D18). */
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

    /** An entry over several days: "bis" and when it ends, a day ("Sonntag, 22. November") or a time ("01:00"). */
    data class Until(
        val end: String,
    ) : Addition
}

/**
 * The days of the week's rows from the entries (`calendar_days`, shared with
 * the month as [CalendarDays]). Every day shows, also an empty one; an entry
 * stands on its first day, one that began before the first day on that; past
 * entries stay. An entry over several days says "bis" and its last day, and
 * each further day shown inside it carries its title in [WeekDay.running].
 * Timed entries ending today are not marked over.
 */
object WeekLayout {
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val dayAndMonth = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

    /** The days of dates, which run on without a gap. */
    fun build(
        dates: List<LocalDate>,
        entries: List<Entry>,
        today: LocalDate,
    ): List<WeekDay> {
        val placed = CalendarDays.place(dates, entries, keepFirstMidnight = false)
        val running = mutableMapOf<LocalDate, MutableList<String>>()
        for (entry in placed.filter { it.multi }) {
            for (date in dates.filter { it.isAfter(entry.date) && !it.isAfter(entry.last) }) {
                running.getOrPut(date) { mutableListOf() } += entry.entry.title
            }
        }
        val days = CalendarDays.byDay(dates, placed)
        return dates.mapIndexed { index, date ->
            WeekDay(
                date = date,
                today = date == today,
                past = date.isBefore(today),
                entries = days[index].map(::dayEntry),
                running = running[date].orEmpty(),
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
        val end = last.format(dayAndMonth)
        return if (YearMonth.from(first) == YearMonth.from(last)) {
            "${first.dayOfMonth}.–$end"
        } else {
            "${first.format(dayAndMonth)} – $end"
        }
    }

    /** An entry as it stands on its day: its time when it is timed there, and its addition. */
    private fun dayEntry(placed: CalendarDays.Placed): DayEntry {
        val entry = placed.entry
        // A year of birth after the entry's year is wrong data; it gets no age.
        val age = entry.year?.let { entry.start.year - it }?.takeIf { it >= 0 }
        val addition =
            when {
                entry.kind == Kinds.HOLIDAY -> Addition.Holiday
                entry.kind == Kinds.BIRTHDAY && age != null -> Addition.Turns(age)
                placed.multi -> Addition.Until(until(placed))
                else -> null
            }
        val time =
            when {
                !placed.timed -> null
                placed.multi || !entry.end.isAfter(entry.start) -> entry.start.format(clock)
                else -> "${entry.start.format(clock)}–${endOfDay(entry.end, placed.first)}"
            }
        return DayEntry(entry, time, addition)
    }

    /**
     * Where an entry over several days ends, as its "bis" says: a timed one
     * that began before the day it stands on and ends on it, its time; any
     * other, its last day.
     */
    private fun until(placed: CalendarDays.Placed): String =
        if (!placed.entry.allDay && placed.first.isBefore(placed.date) && placed.last == placed.date) {
            endOfDay(placed.entry.end, placed.date)
        } else {
            placed.last.longName
        }

    /** A time that ends on day, "24:00" for the midnight after it. */
    private fun endOfDay(
        end: LocalDateTime,
        day: LocalDate,
    ): String = if (end.toLocalDate() == day) end.format(clock) else MIDNIGHT

    private const val MIDNIGHT = "24:00"
}
