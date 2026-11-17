package dev.cupr.calendar.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A calendar of the sensor: its id, as entries name it, and its name in Home Assistant, when it has one. */
data class SensorCalendar(
    val id: String,
    val name: String? = null,
) {
    /** What a widget's setup shows for it: its name, else its id. */
    val shown: String get() = name ?: id
}

/**
 * The sensor's attribute `calendars`, the calendars it reads in the
 * dashboard's order, `[{id, name}]` (D57); a widget's setup lists them by
 * name. Read leniently until its form is final: an item without a string
 * id, or with an id already listed, is left out, a problem for the caller to
 * log; a name that is no string or blank is none, without a problem, and the
 * setup shows the id; an attribute that is no list is none at all, a
 * problem too.
 */
object SensorCalendars {
    class Read(
        /** The calendars, in the attribute's order; none without the attribute or when it is no list. */
        val calendars: List<SensorCalendar>?,
        val problems: List<String>,
    )

    fun read(attribute: JsonElement?): Read =
        when (attribute) {
            null -> Read(null, emptyList())
            is JsonArray -> items(attribute)
            else -> Read(null, listOf("The sensor's calendars are no list"))
        }

    private fun items(attribute: JsonArray): Read {
        val problems = mutableListOf<String>()
        val calendars = mutableListOf<SensorCalendar>()
        attribute.forEachIndexed { index, item ->
            val calendar = (item as? JsonObject)?.let(::calendar)
            when {
                calendar == null -> {
                    problems += "The sensor's calendar $index has no string id; left out"
                }

                calendars.any { it.id == calendar.id } -> {
                    problems += "The sensor's calendar ${calendar.id} is listed twice; left out"
                }

                else -> {
                    calendars += calendar
                }
            }
        }
        return Read(calendars, problems)
    }

    private fun calendar(item: JsonObject): SensorCalendar? {
        val id = item.string("id")?.takeIf { it.isNotBlank() }
        val name = item.string("name")?.takeIf { it.isNotBlank() }
        return id?.let { SensorCalendar(it, name) }
    }
}
