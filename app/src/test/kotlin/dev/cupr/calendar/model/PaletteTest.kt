package dev.cupr.calendar.model

import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDateTime

class PaletteTest {
    private val noon = LocalDateTime.of(2026, 11, 11, 12, 0)
    private val entry =
        Entry(start = noon, end = noon.plusHours(1), allDay = false, title = "T", calendar = SampleData.BIRTHDAYS)

    @Test
    fun `an entry's own colour wins`() {
        assertEquals(0xFF123456.toInt(), SampleData.dark.colorOf(entry.copy(color = 0xFF123456.toInt())))
    }

    @Test
    fun `an entry without its own colour takes its calendar's colour of the mode`() {
        assertEquals(0xFFFFD700.toInt(), SampleData.dark.colorOf(entry))
        assertEquals(0xFFA38A00.toInt(), SampleData.light.colorOf(entry))
    }

    @Test
    fun `an entry of an unknown calendar without its own colour is the secondary text colour`() {
        assertEquals(SampleData.dark.secondaryText, SampleData.dark.colorOf(entry.copy(calendar = "calendar.other")))
    }

    @Test
    fun `the four fixed calendars take their theme colours, as the dashboard names them`() {
        val colours =
            listOf("calendar.contact_birthdays", "calendar.schulferien_hessen", "calendar.deutschland_he", "special")
                .map { SampleData.light.colorOf(entry.copy(calendar = it)) }
        assertEquals(listOf(0xFFA38A00, 0xFF2D9D92, 0xFF8C8C8C, 0xFFD67500).map { it.toInt() }, colours)
    }

    @Test
    fun `a calendar whose id starts with calendar_family_ takes the family colour, any other the secondary text`() {
        assertEquals(0xFF3CB371.toInt(), SampleData.dark.colorOf(entry.copy(calendar = "calendar.family_someone")))
        assertEquals(0xFF359F65.toInt(), SampleData.light.colorOf(entry.copy(calendar = "calendar.family_someone")))
        for (other in listOf("calendar.family", "calendar.familyx", "calendar.personal_someone", "family_x")) {
            assertEquals(other, SampleData.light.secondaryText, SampleData.light.colorOf(entry.copy(calendar = other)))
        }
    }

    @Test
    fun `a palette has a colour for each calendar variable, no fewer and no more`() {
        val calendars = Palettes.BUILT_IN.light.calendars
        assertThrows(IllegalArgumentException::class.java) {
            Palettes.BUILT_IN.light.copy(calendars = calendars - CalendarVariables.SPECIAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Palettes.BUILT_IN.light.copy(calendars = calendars + ("zuhause-calendar-other" to 0))
        }
    }
}
