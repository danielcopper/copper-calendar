package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
        val width = { weight: Int ->
            Paint()
                .apply {
                    typeface = fonts.weight(weight)
                    textSize = 40f
                }.measureText("Kindergeburtstag 2026")
        }
        assertTrue("700 is not wider than 400", width(700) > width(400))
    }

    @Test
    fun `a font that does not load fails, naming its file and weight`() {
        val error = assertThrows(IllegalStateException::class.java) { Fonts(assets, "fonts/missing.ttf").weight(500) }
        assertEquals("The font fonts/missing.ttf does not load at weight 500", error.message)
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
