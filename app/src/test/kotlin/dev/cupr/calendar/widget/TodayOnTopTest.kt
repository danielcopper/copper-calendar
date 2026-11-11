package dev.cupr.calendar.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a widget's list scrolls so today's row stands on top ([TodayOnTop.target]). */
class TodayOnTopTest {
    private companion object {
        /** The smooth scroll's room below a target that is not the last row: 12 dp at density 1. */
        const val EXTRA = 12
    }

    private val heights = listOf(100, 100, 100, 120, 100, 100)

    @Test
    fun `the target is the last row that fits below today, so today stands on top`() {
        // Rows 2, 3 and 4 are 320 px; row 5 would make 420. The padding is what they leave of the list.
        assertEquals(TodayOnTop.Target(4, 320, 80), TodayOnTop.target(heights, today = 2, listPx = 400f, EXTRA))
    }

    @Test
    fun `a row that fits exactly with the scroll's room below it is still the target`() {
        assertEquals(TodayOnTop.Target(4, 320, 12), TodayOnTop.target(heights, today = 2, listPx = 332f, EXTRA))
    }

    @Test
    fun `a row other than the last leaves the scroll's room below it`() {
        // Rows 2 to 4 would fit into 330 px, but not into 330 less the room.
        assertEquals(TodayOnTop.Target(3, 220, 110), TodayOnTop.target(heights, today = 2, listPx = 330f, EXTRA))
    }

    @Test
    fun `the last row needs no room below it`() {
        // Rows 2 to 5 are 420 px: within 425, though not within 425 less the room.
        assertEquals(TodayOnTop.Target(5, 420, 0), TodayOnTop.target(heights, today = 2, listPx = 425f, EXTRA))
    }

    @Test
    fun `when the rows from today on do not fill the list, the target is the last row`() {
        assertEquals(TodayOnTop.Target(5, 420, 0), TodayOnTop.target(heights, today = 2, listPx = 1000f, EXTRA))
    }

    @Test
    fun `when today is the last row, it is the target`() {
        assertEquals(TodayOnTop.Target(5, 100, 0), TodayOnTop.target(heights, today = 5, listPx = 400f, EXTRA))
    }

    @Test
    fun `when today's row alone is taller than the list, it is the target, without padding`() {
        assertEquals(TodayOnTop.Target(3, 120, 0), TodayOnTop.target(heights, today = 3, listPx = 90f, EXTRA))
    }

    @Test
    fun `when today is the last row and taller than the list, it is the target, without padding`() {
        assertEquals(TodayOnTop.Target(5, 100, 0), TodayOnTop.target(heights, today = 5, listPx = 90f, EXTRA))
    }

    @Test
    fun `when today's row alone fits the list but not with the scroll's room, it gets no padding`() {
        assertEquals(TodayOnTop.Target(3, 120, 0), TodayOnTop.target(heights, today = 3, listPx = 125f, EXTRA))
    }

    @Test
    fun `the padding is rounded down`() {
        assertEquals(TodayOnTop.Target(4, 320, 80), TodayOnTop.target(heights, today = 2, listPx = 400.7f, EXTRA))
    }

    @Test
    fun `without a row for today there is no target`() {
        assertNull(TodayOnTop.target(heights, today = -1, listPx = 400f, EXTRA))
        assertNull(TodayOnTop.target(heights, today = heights.size, listPx = 400f, EXTRA))
        assertNull(TodayOnTop.target(emptyList(), today = 0, listPx = 400f, EXTRA))
    }
}
