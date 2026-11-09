package dev.cupr.calendar.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EntryTest {
    private val day = LocalDate.of(2026, 11, 12)

    private fun entry(
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean,
    ) = Entry(start = start, end = end, allDay = allDay, title = "T", calendar = "calendar.family")

    @Test
    fun `an entry ending before it starts is refused`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                entry(day.atTime(10, 0), day.atTime(9, 0), allDay = false)
            }
        assertEquals("The entry \"T\" ends before it starts", error.message)
    }

    @Test
    fun `a timed entry may end where it starts`() {
        entry(day.atTime(10, 0), day.atTime(10, 0), allDay = false)
    }

    @Test
    fun `an all-day entry runs from midnight to a later midnight`() {
        entry(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), allDay = true)
        val offMidnight =
            assertThrows(IllegalArgumentException::class.java) {
                entry(day.atTime(1, 0), day.plusDays(1).atStartOfDay(), allDay = true)
            }
        assertEquals("The all-day entry \"T\" does not run from midnight to midnight", offMidnight.message)
        val empty =
            assertThrows(IllegalArgumentException::class.java) {
                entry(day.atStartOfDay(), day.atStartOfDay(), allDay = true)
            }
        assertEquals("The all-day entry \"T\" covers no day", empty.message)
    }
}
