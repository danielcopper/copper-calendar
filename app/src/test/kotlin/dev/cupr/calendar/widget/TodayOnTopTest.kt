package dev.cupr.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a widget's list scrolls so today's row stands on top ([TodayOnTop.target]). */
class TodayOnTopTest {
    private val heights = listOf(100, 100, 100, 120, 100, 100)

    @Test
    fun `the target is the last row that fits below today, so today stands on top`() {
        // Rows 2, 3 and 4 are 320 px; row 5 would make 420.
        assertEquals(TodayOnTop.Target(4, 320), TodayOnTop.target(heights, today = 2, listPx = 400f))
    }

    @Test
    fun `a row that fits exactly is still the target`() {
        assertEquals(TodayOnTop.Target(4, 320), TodayOnTop.target(heights, today = 2, listPx = 320f))
    }

    @Test
    fun `when the rows after today do not fill the list, the target is the last row`() {
        assertEquals(TodayOnTop.Target(5, 420), TodayOnTop.target(heights, today = 2, listPx = 1000f))
    }

    @Test
    fun `when today is the last row, it is the target`() {
        assertEquals(TodayOnTop.Target(5, 100), TodayOnTop.target(heights, today = 5, listPx = 400f))
    }

    @Test
    fun `when today's row alone is taller than the list, it is the target`() {
        assertEquals(TodayOnTop.Target(3, 120), TodayOnTop.target(heights, today = 3, listPx = 90f))
    }

    @Test
    fun `without a row for today there is no target`() {
        assertNull(TodayOnTop.target(heights, today = -1, listPx = 400f))
        assertNull(TodayOnTop.target(heights, today = heights.size, listPx = 400f))
        assertNull(TodayOnTop.target(emptyList(), today = 0, listPx = 400f))
    }
}
