package dev.cupr.calendar.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CssColorsTest {
    @Test
    fun `a CSS colour name is read in any case, opaque`() {
        assertEquals(0xFFFF69B4.toInt(), CssColors.parse("hotpink"))
        assertEquals(0xFF663399.toInt(), CssColors.parse("RebeccaPurple"))
        assertEquals(0xFF808080.toInt(), CssColors.parse(" grey "))
    }

    @Test
    fun `a hex colour is read with six digits or three`() {
        assertEquals(0xFF1F6FD6.toInt(), CssColors.parse("#1f6fd6"))
        assertEquals(0xFFAABBCC.toInt(), CssColors.parse("#ABC"))
    }

    @Test
    fun `anything else is no colour`() {
        for (value in listOf("", "#", "#12345", "#1234567", "#gggggg", "1f6fd6", "nocolour", "rgb(1, 2, 3)")) {
            assertNull(value, CssColors.parse(value))
        }
    }

    @Test
    fun `every named colour of CSS Color 4 is known`() {
        // 148 names, among them the gray/grey pairs and rebeccapurple; spot checks from both ends of the list.
        val names = listOf("aliceblue", "darkslategrey", "lightgoldenrodyellow", "mediumspringgreen", "yellowgreen")
        names.forEach { assertNotNull(it, CssColors.parse(it)) }
        assertEquals(148, CssColors.namedCount)
    }
}
