package dev.cupr.calendar.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The checks on a display density and on opacities. */
class ScaleTest {
    @Test
    fun `a density is above 0`() {
        val error = assertThrows(IllegalArgumentException::class.java) { Scale(0f) }
        assertEquals("A display density is above 0, not 0.0", error.message)
    }

    @Test
    fun `an opacity runs from 0 to 1`() {
        assertEquals(0x80FF0000.toInt(), 0xFFFF0000.toInt().withOpacity(0.5f))
        assertEquals(153, opacityAlpha(0.6f))
        val colour = assertThrows(IllegalArgumentException::class.java) { 0xFFFF0000.toInt().withOpacity(1.5f) }
        assertEquals("An opacity runs from 0 to 1, not 1.5", colour.message)
        val layer = assertThrows(IllegalArgumentException::class.java) { opacityAlpha(-0.1f) }
        assertEquals("An opacity runs from 0 to 1, not -0.1", layer.message)
    }
}
