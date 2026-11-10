package dev.cupr.calendar.model

import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
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
}
