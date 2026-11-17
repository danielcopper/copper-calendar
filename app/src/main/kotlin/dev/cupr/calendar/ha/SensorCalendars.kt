package dev.cupr.calendar.ha

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A calendar of the sensor: its id, as entries name it, and its name in Home Assistant, when it has one. */
@Serializable
data class SensorCalendar(
    val id: String,
    val name: String? = null,
)

/**
 * The sensor's attribute `calendars`, the calendars it reads in the
 * dashboard's order, `[{id, name}]` (homelab, plan of cut 4); a widget's
 * setup lists them by name. Read leniently until its form is final: an
 * item without a string id, or with an id already listed, is left out, a
 * name that is no string is none, and each is a problem for the caller to
 * log; an attribute that is no list is none at all.
 */
object SensorCalendars {
    class Read(
        /** The calendars, in the attribute's order; none without the attribute or when it is no list. */
        val calendars: List<SensorCalendar>?,
        val problems: List<String>,
    )

    fun read(attribute: JsonElement?): Read {
        if (attribute == null) return Read(null, emptyList())
        if (attribute !is JsonArray) return Read(null, listOf("The sensor's calendars are no list"))
        val problems = mutableListOf<String>()
        val calendars = mutableListOf<SensorCalendar>()
        attribute.forEachIndexed { index, item ->
            val calendar = (item as? JsonObject)?.let(::calendar)
            when {
                calendar == null -> {
                    problems += "The sensor's calendar $index has no id; left out"
                }

                calendars.any { it.id == calendar.id } -> {
                    problems += "The sensor's calendar ${calendar.id} is listed twice"
                }

                else -> {
                    calendars += calendar
                }
            }
        }
        return Read(calendars, problems)
    }

    private fun calendar(item: JsonObject): SensorCalendar? {
        val id = (item["id"] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        val name = (item["name"] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
        return id?.let { SensorCalendar(it, name) }
    }
}
