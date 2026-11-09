package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.month.Day
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** A timed entry's mark: its drawing, else its icon, else the dot (D22). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val icons = Icons(assets)
    private val drawings = Drawings(assets)
    private val rows = WeekRowRenderer(Fonts(assets), icons, drawings)
    private val scale = Scale(DENSITY)
    private val monday = LocalDate.of(2026, 11, 16)
    private val color = 0xFFFF0000.toInt()

    @Test
    fun `ships the icons Home Assistant names for the kinds and the widgets' own`() {
        assertNotNull(icons.path("mdi:swim"))
        assertNotNull(icons.path("mdi:home-heart"))
        assertNotNull(icons.path("chevron-right"))
        assertNotNull(icons.path("plus"))
        assertNull(icons.path("mdi:does-not-exist"))
    }

    @Test
    fun `ships the sample month's drawings and knows no others`() {
        for (name in listOf("halloween.svg", "lantern.svg", "candles-1.svg", "boot.svg")) {
            assertNotNull(name, drawings.get(name))
        }
        assertNull(drawings.get("does-not-exist.svg"))
    }

    @Test
    fun `an icon the app does not ship draws the entry's dot`() {
        val bitmap = render(entry(icon = "mdi:does-not-exist"))
        assertEquals(color, bitmap.getPixel(markX(MARK_CENTRE), lineY()))
    }

    @Test
    fun `an entry without an icon draws the dot`() {
        val bitmap = render(entry(icon = null))
        assertEquals(color, bitmap.getPixel(markX(MARK_CENTRE), lineY()))
    }

    @Test
    fun `a shipped icon draws its outline in the entry's colour, not the dot`() {
        // chevron-right is open at the box's centre and filled at x = 14.5 of 24.
        val bitmap = render(entry(icon = "mdi:chevron-right"))
        assertEquals(0, bitmap.getPixel(markX(MARK_CENTRE), lineY()))
        assertEquals(color, bitmap.getPixel(markX(CHEVRON_FILLED), lineY()))
    }

    private fun entry(icon: String?) =
        Entry(
            start = monday.atTime(10, 0),
            end = monday.atTime(11, 0),
            allDay = false,
            title = "Termin",
            calendar = SampleData.FAMILY,
            color = color,
            icon = icon,
        )

    private fun render(entry: Entry): Bitmap {
        val days =
            (0L..6L).map { offset ->
                val date = monday.plusDays(offset)
                Day(
                    date = date,
                    otherMonth = false,
                    past = false,
                    today = false,
                    weekend = offset >= 5,
                    holiday = false,
                    drawings = emptyList(),
                    shown = if (offset == 0L) listOf(entry) else emptyList(),
                    more = 0,
                )
            }
        return rows.render(Week(days, emptyList(), 0), WIDTH_PX, scale, SampleData.dark)
    }

    /** x of a point of the mark's 24-unit box in Monday's first line. */
    private fun markX(unit: Float) = (scale.px(2f + 1f) + scale.px(11f) * unit / 24f).toInt()

    /** y of the middle of Monday's first line. */
    private fun lineY() = (scale.hairline + scale.px(3f + 22f + 2f + 2f + 14.5f / 2)).toInt()

    private companion object {
        const val DENSITY = 2.625f
        const val WIDTH_PX = 656
        const val MARK_CENTRE = 12f
        const val CHEVRON_FILLED = 14.5f
    }
}
