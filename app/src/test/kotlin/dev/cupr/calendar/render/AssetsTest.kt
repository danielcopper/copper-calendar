package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssetsTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    @Test
    fun `Roboto loads with a working weight axis`() {
        val fonts = Fonts(assets)
        val width = { weight: FontWeight ->
            Paint()
                .apply {
                    typeface = fonts.weight(weight)
                    textSize = 40f
                }.measureText("Kindergeburtstag 2026")
        }
        assertTrue("700 is not wider than 400", width(FontWeight.BOLD) > width(FontWeight.REGULAR))
    }

    @Test
    fun `a font that does not load fails, naming its file and weight`() {
        val error =
            assertThrows(
                IllegalStateException::class.java,
            ) { Fonts(assets, "fonts/missing.ttf").weight(FontWeight.MEDIUM) }
        assertEquals("The font fonts/missing.ttf does not load at weight 500", error.message)
    }

    @Test
    fun `every shipped icon and drawing parses`() {
        val icons = Icons(assets)
        assertEquals(71, icons.names.size)
        for (name in icons.names) assertNotNull(name, icons.path(name))
        val drawings = Drawings(assets)
        assertEquals(setOf("halloween.svg", "lantern.svg", "candles-1.svg", "boot.svg"), drawings.names)
        for (name in drawings.names) assertNotNull(name, drawings.get(name))
    }

    @Test
    fun `every icon and drawing the sample month names is shipped`() {
        val icons = Icons(assets)
        val drawings = Drawings(assets)
        for (entry in SampleData.entries) {
            entry.icon?.let { assertNotNull(it, icons.path(it)) }
            entry.drawing?.let { assertNotNull(it, drawings.get(it)) }
        }
        assertTrue(ShadowLog.getLogsForTag("RenderAssets").isEmpty())
    }

    @Test
    fun `the icon list refuses a line without path data and a name given twice`() {
        assertEquals(mapOf("plus" to "M19,13Z"), parseIcons(sequenceOf("# a comment", "", "plus M19,13Z")))
        val bare = assertThrows(IllegalArgumentException::class.java) { parseIcons(sequenceOf("plus")) }
        assertEquals("icons/mdi.txt: a line without path data: plus", bare.message)
        val twice =
            assertThrows(IllegalArgumentException::class.java) { parseIcons(sequenceOf("plus M1Z", "plus M2Z")) }
        assertEquals("icons/mdi.txt names these icons twice: [plus]", twice.message)
    }

    @Test
    fun `an icon the app does not ship is logged once per name`() {
        val icons = Icons(assets)
        icons.path("mdi:not-shipped")
        icons.path("mdi:not-shipped")
        icons.path("mdi:also-not-shipped")
        icons.path("mdi:swim")
        assertEquals(
            listOf("The app ships no icon named mdi:not-shipped", "The app ships no icon named mdi:also-not-shipped"),
            ShadowLog.getLogsForTag("RenderAssets").map { it.msg },
        )
    }

    @Test
    fun `a drawing the app does not ship is logged once per name`() {
        val drawings = Drawings(assets)
        drawings.get("not-shipped.svg")
        drawings.get("not-shipped.svg")
        drawings.get("lantern.svg")
        assertEquals(
            listOf("The app ships no drawing named not-shipped.svg"),
            ShadowLog.getLogsForTag("RenderAssets").map { it.msg },
        )
    }
}
