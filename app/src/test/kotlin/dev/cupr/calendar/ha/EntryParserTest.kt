package dev.cupr.calendar.ha

import dev.cupr.calendar.model.CalendarVariables
import dev.cupr.calendar.model.DashboardColor
import dev.cupr.calendar.model.Entry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EntryParserTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun parse(json: String) = EntryParser.parse(Json.parseToJsonElement(json).jsonArray, berlin)

    /** An entry as the sensor delivers it: a birthday of 2026-11-14 with every field it can have. */
    private val birthday =
        """{"start":"2026-11-14","end":"2026-11-15","all_day":true,"title":"Anna","kind":"birthday",""" +
            """"icon":"mdi:cake-variant","calendar":"calendar.contact_birthdays","color":"hotpink",""" +
            """"categories":["Familie"],"year":2000,"uid":"b-1","recurrence_id":"2026-11-14"}"""

    private val timed =
        """{"start":"2026-11-10T15:30:00+01:00","end":"2026-11-10T16:15:00+01:00","all_day":false,""" +
            """"title":"Zahnarzt Ben","kind":"dentist","icon":"mdi:tooth-outline",""" +
            """"calendar":"calendar.family","color":"#87cefa","drawing":"lantern.svg","uid":"t-1"}"""

    @Test
    fun `an all-day entry runs from the midnight of its first day to the one after its last`() {
        val entry = parse("[$birthday]").entries.single()

        assertEquals(
            Entry(
                start = LocalDate.of(2026, 11, 14).atStartOfDay(),
                end = LocalDate.of(2026, 11, 15).atStartOfDay(),
                allDay = true,
                title = "Anna",
                calendar = "calendar.contact_birthdays",
                kind = "birthday",
                color = 0xFFFF69B4.toInt(),
                icon = "mdi:cake-variant",
                categories = listOf("Familie"),
                year = 2000,
                uid = "b-1",
            ),
            entry,
        )
    }

    @Test
    fun `a timed entry's times are read as wall-clock times in the zone`() {
        val entry = parse("[$timed]").entries.single()

        assertEquals(LocalDateTime.of(2026, 11, 10, 15, 30), entry.start)
        assertEquals(LocalDateTime.of(2026, 11, 10, 16, 15), entry.end)
        assertEquals(0xFF87CEFA.toInt(), entry.color)
        assertEquals("lantern.svg", entry.drawing)

        val utc = EntryParser.parse(Json.parseToJsonElement("[$timed]").jsonArray, ZoneId.of("UTC")).entries.single()
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 30), utc.start)
    }

    @Test
    fun `an entry with only the fields every entry has is read, the rest empty`() {
        val entry =
            parse(
                """[{"start":"2026-11-11","end":"2026-11-12","all_day":true,""" +
                    """"title":"Martinstag","calendar":"special"}]""",
            ).entries.single()

        assertNull(entry.kind)
        assertNull(entry.color)
        assertNull(entry.icon)
        assertNull(entry.uid)
        assertEquals(emptyList<String>(), entry.categories)
    }

    @Test
    fun `an entry that cannot be read is left out and named, and the others are kept`() {
        val bad =
            listOf(
                """{"end":"2026-11-15","all_day":true,"title":"No start","calendar":"c"}""",
                """{"start":"2026-11-14","end":"2026-11-15","all_day":"yes","title":"Not a boolean","calendar":"c"}""",
                """{"start":"14.11.2026","end":"2026-11-15","all_day":true,"title":"Bad date",""" +
                    """"calendar":"c","uid":"u-3"}""",
                """{"start":"2026-11-14T10:00:00","end":"2026-11-14T11:00:00","all_day":false,""" +
                    """"title":"No offset","calendar":"c"}""",
                """{"start":"2026-11-14","end":"2026-11-15","all_day":true,"title":"Odd colour",""" +
                    """"calendar":"c","color":"nocolour"}""",
                """{"start":"2026-11-15","end":"2026-11-14","all_day":true,"title":"Backwards","calendar":"c"}""",
                """"not an object"""",
            )

        val parsed = parse("[$birthday,${bad.joinToString(",")},$timed]")

        assertEquals(listOf("Anna", "Zahnarzt Ben"), parsed.entries.map { it.title })
        assertEquals(2, parsed.read.size)
        assertEquals(7, parsed.problems.size)
        // One line each, without the JSON input the decoder's message goes on with.
        assertTrue(parsed.problems.joinToString("\n"), parsed.problems.none { "\n" in it })
        val problems = parsed.problems.joinToString("\n")
        assertTrue(problems, problems.contains("Entry 1 (\"No start\") left out: Field 'start' is required"))
        assertTrue(problems, problems.contains("Entry 2 (\"Not a boolean\") left out: Failed to parse literal"))
        assertTrue(problems, problems.contains("Entry 3 (uid u-3, \"Bad date\") left out:"))
        assertTrue(problems, problems.contains("Entry 5 (\"Odd colour\") left out: unknown colour \"nocolour\""))
        assertTrue(
            problems,
            problems.contains("Entry 6 (\"Backwards\") left out: The entry \"Backwards\" ends before it starts"),
        )
        assertTrue(problems, problems.contains("Entry 7 left out: not an object"))
    }

    @Test
    fun `a birth year after the entry's year is dropped and named, so no age is shown`() {
        val future = birthday.replace("\"year\":2000", "\"year\":2027")

        val parsed = parse("[$future]")

        assertNull(parsed.entries.single().year)
        assertEquals(
            listOf("Entry 0 (uid b-1, \"Anna\"): birth year 2027 is after the entry's year 2026, so no age is shown"),
            parsed.problems,
        )
        assertFalse(
            parsed.read
                .single()
                .json
                .containsKey("year"),
        )
    }

    @Test
    fun `a birth year in the entry's year is kept`() {
        val parsed = parse("[${birthday.replace("\"year\":2000", "\"year\":2026")}]")

        assertEquals(2026, parsed.entries.single().year)
        assertEquals(emptyList<String>(), parsed.problems)
    }

    /** An entry of calendar.a with its own colour and the dashboard's colour, sent as resolved. */
    private fun resolved(color: String) =
        """{"start":"2026-11-14","end":"2026-11-15","all_day":true,"title":"Resolved","calendar":"calendar.a",""" +
            """"color":"hotpink","resolved_color":$color}"""

    @Test
    fun `the dashboard's colour is read as a fixed colour from hex or a CSS colour name`() {
        val parsed = parse("[${resolved("\"#8855a8\"")},${resolved("\"steelblue\"")},${resolved("\"#abc\"")}]")

        assertEquals(
            listOf(0xFF8855A8, 0xFF4682B4, 0xFFAABBCC).map { DashboardColor.Fixed(it.toInt()) },
            parsed.entries.map { it.dashboardColor },
        )
        assertEquals(emptyList<String>(), parsed.problems)
    }

    @Test
    fun `the dashboard's colour is read as each calendar's theme variable or the secondary text colour`() {
        // The names homelab's zuhause.jinja sends (macro calendar_color), written out.
        val calendars = listOf("family", "birthdays", "school", "public", "special").map { "zuhause-calendar-$it" }

        val parsed =
            parse((calendars + "secondary-text-color").joinToString(",", "[", "]") { resolved("\"var(--$it)\"") })

        assertEquals(
            calendars.map { DashboardColor.CalendarVariable(it) } + DashboardColor.SecondaryText,
            parsed.entries.map { it.dashboardColor },
        )
        assertEquals(emptyList<String>(), parsed.problems)
    }

    @Test
    fun `the dashboard's colour is read in any case and with spaces, as a fixed colour is`() {
        val sent =
            listOf(
                "\" VAR(--Zuhause-Calendar-Family) \"",
                "\"var( --secondary-text-color )\"",
                "\" #8855A8 \"",
                "\"SteelBlue\"",
            )

        val parsed = parse(sent.joinToString(",", "[", "]") { resolved(it) })

        assertEquals(
            listOf(
                DashboardColor.CalendarVariable(CalendarVariables.FAMILY),
                DashboardColor.SecondaryText,
                DashboardColor.Fixed(0xFF8855A8.toInt()),
                DashboardColor.Fixed(0xFF4682B4.toInt()),
            ),
            parsed.entries.map { it.dashboardColor },
        )
        assertEquals(emptyList<String>(), parsed.problems)
    }

    @Test
    fun `a dashboard's colour the app cannot read is dropped and named, and the entry kept with its own colour`() {
        val unreadable =
            listOf(
                "\"nocolour\"",
                "\"var(--zuhause-calendar-other)\"",
                "\"var(--primary-color)\"",
                "\"var(--zuhause-calendar-family\"",
                "\"var(--zuhause-calendar-family, red)\"",
                "\"\"",
                "12",
                "{}",
            )

        val parsed = parse(unreadable.joinToString(",", "[", "]") { resolved(it) })

        assertEquals(List(unreadable.size) { "Resolved" }, parsed.entries.map { it.title })
        assertEquals(List(unreadable.size) { null }, parsed.entries.map { it.dashboardColor })
        assertEquals(List(unreadable.size) { 0xFFFF69B4.toInt() }, parsed.entries.map { it.color })
        assertEquals(
            unreadable.mapIndexed { index, value ->
                "Entry $index (\"Resolved\"): resolved_color $value is no colour the app reads, " +
                    "so its own or its calendar's applies"
            },
            parsed.problems,
        )
        assertTrue(parsed.read.none { it.json.containsKey("resolved_color") })
    }

    @Test
    fun `an own colour the app cannot read leaves the entry out, also with the dashboard's colour`() {
        val parsed = parse("[${resolved("\"#8855a8\"").replace("hotpink", "nocolour")}]")

        assertEquals(emptyList<Entry>(), parsed.entries)
        assertEquals(listOf("Entry 0 (\"Resolved\") left out: unknown colour \"nocolour\""), parsed.problems)
    }

    @Test
    fun `an entry without the dashboard's colour, or with null, has none and no problem`() {
        val without = resolved("null").replace(",\"resolved_color\":null", "")

        val parsed = parse("[$without,${resolved("null")}]")

        assertEquals(listOf(null, null), parsed.entries.map { it.dashboardColor })
        assertEquals(emptyList<String>(), parsed.problems)
    }

    @Test
    fun `the kept entries read again give the same entries without problems`() {
        val first =
            parse(
                "[$birthday,$timed,${birthday.replace("\"year\":2000", "\"year\":2030")}," +
                    "${resolved("\"var(--zuhause-calendar-family)\"")},${resolved("\"#8855a8\"")}," +
                    "${resolved("\"var(--other)\"")}," +
                    resolved("\"var(--other)\"").replace("}", ",\"year\":2030}") + "]",
            )

        val again = EntryParser.parse(JsonArray(first.read.map { it.json }), berlin)

        assertEquals(JsonPrimitive("var(--zuhause-calendar-family)"), first.read[3].json["resolved_color"])
        // Both dropped from the one entry: its birth year and the dashboard's colour.
        assertFalse(first.read[6].json.containsKey("year"))
        assertFalse(first.read[6].json.containsKey("resolved_color"))
        assertEquals(2, first.problems.count { it.startsWith("Entry 6 ") })
        assertEquals(DashboardColor.CalendarVariable(CalendarVariables.FAMILY), again.entries[3].dashboardColor)
        assertEquals(first.entries, again.entries)
        assertEquals(emptyList<String>(), again.problems)
    }
}
