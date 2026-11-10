package dev.cupr.calendar.model

import androidx.annotation.ColorInt
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One calendar entry as a source delivers it (D4). The widgets depend on this
 * shape only: a source fills it, and nothing about an entry's kind, icon,
 * drawing or colour is worked out in the app.
 *
 * Times are local wall-clock times. An all-day entry starts at the midnight
 * of its first day and ends at the midnight after its last day, as Home
 * Assistant delivers it; a timed entry ends where it ends.
 */
data class Entry(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val title: String,
    /** The calendar it comes from, such as `calendar.contact_birthdays`. */
    val calendar: String,
    /** Home Assistant's kind (`birthday`, `special`, `holiday`, ...); none for an entry of no kind. */
    val kind: String? = null,
    /** The entry's resolved colour (ARGB); none when it has no own colour, then its calendar's applies. */
    @param:ColorInt val color: Int? = null,
    /** An MDI icon name (`mdi:swim`); none when the entry has no icon. */
    val icon: String? = null,
    /** A drawing's file name (`lantern.svg`); none when the entry has none. */
    val drawing: String? = null,
    val categories: List<String> = emptyList(),
    /** A birthday's year of birth. */
    val year: Int? = null,
    val uid: String? = null,
) {
    init {
        require(!end.isBefore(start)) { "The entry \"$title\" ends before it starts" }
        if (allDay) {
            require(start.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalTime() == LocalTime.MIDNIGHT) {
                "The all-day entry \"$title\" does not run from midnight to midnight"
            }
            require(end.isAfter(start)) { "The all-day entry \"$title\" covers no day" }
        }
    }
}
