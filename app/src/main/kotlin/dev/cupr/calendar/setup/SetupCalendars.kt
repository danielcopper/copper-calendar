package dev.cupr.calendar.setup

import dev.cupr.calendar.ha.SensorCalendar
import dev.cupr.calendar.model.Entry

/**
 * The calendars a widget's setup lists: the sensor's, by name in the
 * dashboard's order, when the snapshot has them; then, by id and sorted,
 * those seen in the snapshot's entries and those left out that the sensor
 * does not list, so each can be ticked again.
 */
fun setupCalendars(
    sensor: List<SensorCalendar>?,
    entries: List<Entry>,
    leftOut: Set<String>,
): List<SensorCalendar> {
    val listed = sensor.orEmpty()
    val known = listed.map { it.id }.toSet()
    val others = (entries.map { it.calendar } + leftOut).filter { it !in known }.distinct().sorted()
    return listed + others.map { SensorCalendar(it) }
}
