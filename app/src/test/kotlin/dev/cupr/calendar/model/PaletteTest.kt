package dev.cupr.calendar.model

import dev.cupr.calendar.model.CalendarVariables.FAMILY
import dev.cupr.calendar.model.CalendarVariables.SCHOOL
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
    fun `the dashboard's fixed colour beats the entry's own colour and its calendar's`() {
        val dashboard = DashboardColor.Fixed(0xFF8855A8.toInt())

        for (palette in listOf(SampleData.light, SampleData.dark)) {
            assertEquals(0xFF8855A8.toInt(), palette.colorOf(entry.copy(dashboardColor = dashboard)))
            assertEquals(
                0xFF8855A8.toInt(),
                palette.colorOf(entry.copy(color = 0xFF123456.toInt(), dashboardColor = dashboard)),
            )
        }
    }

    @Test
    fun `the dashboard's theme variable takes the mode's colour, whatever the calendar`() {
        val family = entry.copy(calendar = "calendar.a", dashboardColor = DashboardColor.ThemeVariable(FAMILY))

        assertEquals(0xFF359F65.toInt(), SampleData.light.colorOf(family))
        assertEquals(0xFF3CB371.toInt(), SampleData.dark.colorOf(family))
        val school = entry.copy(color = 0xFF123456.toInt(), dashboardColor = DashboardColor.ThemeVariable(SCHOOL))
        assertEquals(0xFF2D9D92.toInt(), SampleData.light.colorOf(school))
        assertEquals(0xFF40E0D0.toInt(), SampleData.dark.colorOf(school))
    }

    @Test
    fun `the dashboard's secondary text colour is the mode's, also for a calendar with a colour`() {
        val grey = entry.copy(color = 0xFF123456.toInt(), dashboardColor = DashboardColor.SecondaryText)

        assertEquals(SampleData.light.secondaryText, SampleData.light.colorOf(grey))
        assertEquals(SampleData.dark.secondaryText, SampleData.dark.colorOf(grey))
    }

    @Test
    fun `a theme variable the palettes have no colour for is no dashboard colour`() {
        assertThrows(IllegalArgumentException::class.java) { DashboardColor.ThemeVariable("zuhause-calendar-other") }
    }

    @Test
    fun `without the dashboard's colour, an entry's own colour wins`() {
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
