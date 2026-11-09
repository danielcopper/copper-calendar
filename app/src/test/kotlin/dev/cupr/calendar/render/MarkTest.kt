package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.render.Probe.at
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** An entry's mark: a timed entry's drawing, else its icon, else the dot (D22); an all-day chip's drawing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val icons = Icons(assets)
    private val drawings = Drawings(assets)
    private val rows = WeekRowRenderer(Fonts(assets), icons, drawings)
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
        val bitmap = render(timed(icon = "mdi:does-not-exist"))
        assertEquals(color, bitmap.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
    }

    @Test
    fun `an entry without an icon draws the dot`() {
        val bitmap = render(timed(icon = null))
        assertEquals(color, bitmap.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
    }

    @Test
    fun `a shipped icon draws its outline in the entry's colour, not the dot`() {
        // chevron-right is open at the box's centre and filled at x = 14.5 of 24.
        val bitmap = render(timed(icon = "mdi:chevron-right"))
        assertEquals(0, bitmap.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
        assertEquals(color, bitmap.at(Probe.markX(0, CHEVRON_FILLED), Probe.lineMiddle(0)))
    }

    @Test
    fun `a timed entry's drawing wins over its icon`() {
        // plus is filled at the box's centre in the entry's colour; the lantern is not red there.
        val withIcon = render(timed(icon = "mdi:plus"))
        assertEquals(color, withIcon.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
        val withDrawing = render(timed(icon = "mdi:plus", drawing = "lantern.svg"))
        assertNotEquals(color, withDrawing.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
    }

    @Test
    fun `an all-day chip draws its drawing before the title`() {
        val plain = render(allDay(drawing = null))
        val drawn = render(allDay(drawing = "lantern.svg"))
        val x =
            Probe.cellX(0) + Probe.scale.px(PhoneMonth.CHIP_LEFT + PhoneMonth.CHIP_DRAWING_SHIFT + PhoneMonth.MARK / 2)
        val y = Probe.lineMiddle(0)
        assertNotEquals(plain.at(x, y), drawn.at(x, y))
        assertFalse(Color.alpha(drawn.at(x, y)) == 0)
    }

    private fun timed(
        icon: String?,
        drawing: String? = null,
    ) = Entry(
        start = monday.atTime(10, 0),
        end = monday.atTime(11, 0),
        allDay = false,
        title = "Termin",
        calendar = SampleData.FAMILY,
        color = color,
        icon = icon,
        drawing = drawing,
    )

    private fun allDay(drawing: String?) =
        Entry(
            start = monday.atStartOfDay(),
            end = monday.plusDays(1).atStartOfDay(),
            allDay = true,
            title = "Ganztags",
            calendar = SampleData.FAMILY,
            color = color,
            drawing = drawing,
        )

    private fun render(entry: Entry): Bitmap {
        val week = Probe.week(monday, mapOf(0 to Probe.day(monday, shown = listOf(entry))))
        return rows.render(week, Probe.widthPx, Probe.scale, SampleData.dark)
    }

    private companion object {
        const val MARK_CENTRE = 12f
        const val CHEVRON_FILLED = 14.5f
    }
}
