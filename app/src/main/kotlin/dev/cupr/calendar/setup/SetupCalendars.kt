package dev.cupr.calendar.setup

import dev.cupr.calendar.model.Entry

/**
 * The calendars a widget's setup lists, by id, sorted: those seen in the
 * snapshot's entries, and those left out that are not, so each can be
 * ticked again. Shown by id until Home Assistant delivers their names.
 */
fun setupCalendars(
    entries: List<Entry>,
    leftOut: Set<String>,
): List<String> = (entries.map { it.calendar } + leftOut).distinct().sorted()
