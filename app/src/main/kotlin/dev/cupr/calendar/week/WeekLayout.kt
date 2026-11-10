package dev.cupr.calendar.week

import androidx.annotation.StringRes
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import java.text.Collator
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
        return List(DAYS) { first.plusDays(it.toLong()) }
    }

    companion object {
        const val DAYS = 7
    }
}

/**
 * A day of the week's rows ("Woche"), as the dashboard's tile shows it
 * (`calendar_days` with `empty`, `past` and `runs`; the dashboard's file is
 * named in [dev.cupr.calendar.render.PhoneMonth]).
 */
data class WeekDay(
    val date: LocalDate,
    val today: Boolean,
    /** Before today: the whole row is dimmed. */
    val past: Boolean,
    /** A public holiday stands on it: its number is red. */
    val holiday: Boolean,
    /** Its entries, in order. */
    val entries: List<DayEntry>,
    /** The titles of the entries over several days that it lies in after their first day shown. */
    val running: List<String>,
) {
    val weekend: Boolean get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}

/** An entry as a row of the week shows it. */
data class DayEntry(
    val entry: Entry,
    /** Timed within this day: its time ("16:00–16:45"); none for an entry drawn as a chip. */
    val time: String?,
    /** Its short addition after the title; none for no addition. */
    val addition: Addition?,
)

/** An entry's short addition, worded by the app while drawing (D18). */
sealed interface Addition {
    /** A public holiday's "Feiertag". */
    data object Holiday : Addition

    /** A birthday's "wird 26". */
    data class Turns(
        val age: Int,
    ) : Addition

    /** An entry over several days: "bis" and when it ends, a day ("Sonntag, 22. November") or a time ("01:00"). */
    data class Until(
        val end: String,
    ) : Addition
}

/**
 * The days of the week's rows from the entries (`calendar_days`). Every day
 * shows, also an empty one; an entry stands on its first day, one that began
 * before the first day on that; past entries stay. An entry over several days
 * says "bis" and its last day, and each further day shown inside it carries
 * its title in [WeekDay.running]; school holidays and vacations that began
 * before the first day stand on the first day shown instead of making it
 * count. Timed entries ending today are not marked over.
 */
object WeekLayout {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN)
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val dayAndMonth = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMAN)

    fun build(
        dates: List<LocalDate>,
        entries: List<Entry>,
        today: LocalDate,
    ): List<WeekDay> {
        require(dates.isNotEmpty()) { "A week shows at least one day" }
        val begin = dates.first()
        val end = dates.last()
        val placed = mutableMapOf<LocalDate, MutableList<Placed>>()
        val spans = mutableListOf<Span>()
        for (entry in entries) {
            val last = entry.end.minusNanos(1).toLocalDate()
            // Ended before the first day.
            if (!entry.end.isAfter(begin.atStartOfDay())) continue
            val first = entry.start.toLocalDate()
            val date = maxOf(first, begin)
            if (date.isAfter(end)) continue
            val multi = last.isAfter(first)
            val span = entry.allDay && entry.kind in SPAN_KINDS && multi
            val row = placedRow(entry, date, first, last, multi)
            if (multi) {
                spans +=
                    Span(if (span) first else date, last, entry.title, row, listed = !span || !first.isBefore(begin))
            }
            if (span && first.isBefore(begin)) continue
            placed.getOrPut(date) { mutableListOf() } += row
        }
        val running = mutableMapOf<LocalDate, MutableList<String>>()
        for (span in spans) {
            var listed = span.listed
            for (date in dates.filter { it.isAfter(span.first) && !it.isAfter(span.last) }) {
                if (listed) {
                    running.getOrPut(date) { mutableListOf() } += span.title
                } else {
                    placed.getOrPut(date) { mutableListOf() } += span.row
                }
                listed = true
            }
        }
        val order =
            compareBy<Placed>({ it.start != null }, { it.start })
                .then { a, b -> collator.compare(a.day.entry.title, b.day.entry.title) }
        return dates.map { date ->
            val rows = placed[date].orEmpty().sortedWith(order).map { it.day }
            WeekDay(
                date = date,
                today = date == today,
                past = date.isBefore(today),
                holiday = rows.any { it.entry.kind == Kinds.HOLIDAY },
                entries = rows,
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

    /** An entry as it stands on date, and its start when it is timed on that day (the order's key). */
    private data class Placed(
        val day: DayEntry,
        val start: LocalDateTime?,
    )

    /** An entry over several days, as far as its title runs on into the days after the one it stands on. */
    private data class Span(
        val first: LocalDate,
        val last: LocalDate,
        val title: String,
        val row: Placed,
        val listed: Boolean,
    )

    private fun placedRow(
        entry: Entry,
        date: LocalDate,
        first: LocalDate,
        last: LocalDate,
        multi: Boolean,
    ): Placed {
        val timed = !entry.allDay && first == date
        val addition =
            when {
                entry.kind == Kinds.HOLIDAY -> Addition.Holiday
                entry.kind == Kinds.BIRTHDAY && entry.year != null -> Addition.Turns(entry.start.year - entry.year)
                multi -> Addition.Until(until(entry, date, first, last))
                else -> null
            }
        val time =
            when {
                !timed -> null
                multi || !entry.end.isAfter(entry.start) -> entry.start.format(clock)
                else -> "${entry.start.format(clock)}–${endOfDay(entry.end, first)}"
            }
        return Placed(DayEntry(entry, time, addition), if (timed) entry.start else null)
    }

    /**
     * Where an entry over several days ends, as its "bis" says: a timed one
     * that began before date and ends on it, its time; any other, its last day.
     */
    private fun until(
        entry: Entry,
        date: LocalDate,
        first: LocalDate,
        last: LocalDate,
    ): String =
        if (!entry.allDay && first.isBefore(date) && last == date) {
            endOfDay(entry.end, date)
        } else {
            last.format(longDate)
        }

    /** A time that ends on day, "24:00" for the midnight after it. */
    private fun endOfDay(
        end: LocalDateTime,
        day: LocalDate,
    ): String = if (end.toLocalDate() == day) end.format(clock) else MIDNIGHT

    private const val MIDNIGHT = "24:00"
    private val SPAN_KINDS = setOf(Kinds.SCHOOL_HOLIDAYS, Kinds.VACATION)
}
