package dev.cupr.calendar.ha

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorCalendarsTest {
    private fun read(json: String?) = SensorCalendars.read(json?.let(Json::parseToJsonElement))

    @Test
    fun `the calendars are read in their order, by id and name`() {
        val read =
            read("""[{"id":"calendar.b","name":"B"},{"id":"special","name":"Besondere Tage"},{"id":"calendar.a"}]""")

        assertEquals(
            listOf(
                SensorCalendar("calendar.b", "B"),
                SensorCalendar("special", "Besondere Tage"),
                SensorCalendar("calendar.a"),
            ),
            read.calendars,
        )
        assertEquals(emptyList<String>(), read.problems)
    }

    @Test
    fun `without the attribute there are none, and no problem`() {
        val read = read(null)

        assertNull(read.calendars)
        assertEquals(emptyList<String>(), read.problems)
    }

    @Test
    fun `an attribute that is no list gives none, a problem`() {
        val read = read("""{"id":"calendar.a"}""")

        assertNull(read.calendars)
        assertEquals(listOf("The sensor's calendars are no list"), read.problems)
    }

    @Test
    fun `an item without a string id or listed twice is left out, a name that is no string is none`() {
        val read =
            read("""[{"name":"A"},"calendar.x",{"id":3},{"id":" "},{"id":"calendar.a","name":5},{"id":"calendar.a"}]""")

        assertEquals(listOf(SensorCalendar("calendar.a")), read.calendars)
        assertEquals(
            listOf(
                "The sensor's calendar 0 has no id; left out",
                "The sensor's calendar 1 has no id; left out",
                "The sensor's calendar 2 has no id; left out",
                "The sensor's calendar 3 has no id; left out",
                "The sensor's calendar calendar.a is listed twice",
            ),
            read.problems,
        )
    }
}
