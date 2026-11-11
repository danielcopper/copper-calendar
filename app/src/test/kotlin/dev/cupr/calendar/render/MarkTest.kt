package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.render.Probe.RED
import dev.cupr.calendar.render.Probe.at
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * An entry's mark wherever one stands (a timed line, an all-day chip that
 * names a drawing, beside the day's number): its drawing, else its icon,
 * else a dot in its colour, also for a drawing or icon the app does not
 * ship (D22, D24).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val icons = Icons(assets)
    private val drawings = Drawings(assets)
    private val rows = Probe.rows()
    private val monday = LocalDate.of(2026, 11, 16)
    private val scale = Probe.scale

    /** The left edge of an all-day chip's mark in Monday's first line. */
    private val chipMarkX = Probe.cellX(0) + scale.px(PhoneMonth.CHIP_LEFT + PhoneMonth.CHIP_DRAWING_SHIFT)

    /**
     * The drawn width of a 5-cell-wide widget (349 dp by Android's cell-size
     * table, less the card's inset), where a mark beside the number has room
     * for its full size; at 4 cells it is drawn smaller, to fit its cell.
     */
    private val widePx = scale.px(WIDE_DP - 2 * PhoneMonth.CARD_INSET).roundToInt()

    /** The left edge and top of the mark beside Monday's number ("16" is narrower than the number's least width). */
    private val numberMarkX = Probe.cellX(0) + scale.px(PhoneMonth.NUMBER_MIN_WIDTH + PhoneMonth.NUMBER_GAP)
    private val numberMarkY =
        scale.hairline + scale.px(PhoneMonth.CELL_TOP + (PhoneMonth.NUMBER_ROW - PhoneMonth.DRAWING) / 2)

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
        val bitmap = render(Probe.timed(monday, icon = "mdi:does-not-exist"))
        assertEquals(RED, bitmap.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
    }

    @Test
    fun `an entry without an icon draws the dot`() {
        val bitmap = render(Probe.timed(monday, icon = null))
        assertEquals(RED, bitmap.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
    }

    @Test
    fun `a shipped icon draws its outline in the entry's colour, not the dot`() {
        // chevron-right is open at the box's centre and filled at x = 14.5 of 24.
        val bitmap = render(Probe.timed(monday, icon = "mdi:chevron-right"))
        assertEquals(0, bitmap.at(Probe.markX(0, MARK_CENTRE), Probe.lineMiddle(0)))
        assertEquals(RED, bitmap.at(Probe.markX(0, CHEVRON_FILLED), Probe.lineMiddle(0)))
    }

    @Test
    fun `a timed entry's drawing wins over its icon`() {
        val bitmap = render(Probe.timed(monday, icon = "mdi:plus", drawing = "lantern.svg"))
        val left = Probe.markX(0, 0f)
        val top = Probe.lineMiddle(0) - scale.px(PhoneMonth.MARK) / 2
        assertLantern(bitmap, left, top, scale.px(PhoneMonth.MARK))
    }

    @Test
    fun `an all-day chip draws its drawing before the title`() {
        val bitmap = render(Probe.allDay(monday, drawing = "lantern.svg"))
        val top = Probe.lineMiddle(0) - scale.px(PhoneMonth.MARK) / 2
        assertLantern(bitmap, chipMarkX, top, scale.px(PhoneMonth.MARK))
    }

    @Test
    fun `a mark beside the number draws its drawing`() {
        val bitmap = renderMark(Probe.allDay(monday, drawing = "lantern.svg"))
        assertLantern(bitmap, numberMarkX, numberMarkY, scale.px(PhoneMonth.DRAWING))
    }

    @Test
    fun `a mark beside the number with a drawing the app does not ship draws a dot in its colour`() {
        val bitmap = renderMark(Probe.allDay(monday, drawing = "not-shipped.svg"))
        val half = scale.px(PhoneMonth.DRAWING) / 2
        assertEquals(RED, bitmap.at(numberMarkX + half, numberMarkY + half))
    }

    @Test
    fun `a special day of one day without a drawing draws a dot in its colour beside the number`() {
        val special = Probe.allDay(monday).copy(kind = Kinds.SPECIAL)
        val view = MonthLayout.build(YearMonth.from(monday), listOf(special), monday.withDayOfMonth(1))
        val week = view.weeks.single { it.days.first().date == monday }
        val bitmap = rows.render(week, widePx, scale, SampleData.dark)
        val half = scale.px(PhoneMonth.DRAWING) / 2
        assertEquals(RED, bitmap.at(numberMarkX + half, numberMarkY + half))
    }

    @Test
    fun `an all-day chip with a drawing the app does not ship draws its icon`() {
        // plus is filled at the box's centre.
        val bitmap = render(Probe.allDay(monday, drawing = "not-shipped.svg", icon = "mdi:plus"))
        assertEquals(RED, bitmap.at(chipMarkX + scale.px(PhoneMonth.MARK) / 2, Probe.lineMiddle(0)))
    }

    @Test
    fun `a mark beside the number stays inside its own cell at 4 cells wide, the last column's too`() {
        val plain = rows.render(Probe.week(monday), Probe.widthPx, scale, SampleData.dark)
        for (column in listOf(5, 6)) {
            val date = monday.plusDays(column.toLong())
            val special = Probe.allDay(date, drawing = "lantern.svg").copy(kind = Kinds.SPECIAL)
            val marked = Probe.week(monday, mapOf(column to Probe.day(date, marks = listOf(special))))
            val bitmap = rows.render(marked, Probe.widthPx, scale, SampleData.dark)
            val left = Probe.cellX(column)
            val right = Probe.columnX(column + 1) - scale.px(PhoneMonth.CELL_SIDE)
            val changed = (0 until bitmap.width).filter { x -> differs(bitmap, plain, x) }
            assertTrue("column $column draws no mark", changed.isNotEmpty())
            val drawn = "column $column draws at x ${changed.first()} to ${changed.last()}, its cell is $left to $right"
            assertTrue(drawn, changed.first() >= left.toInt() && changed.last() <= right.toInt())
        }
    }

    @Test
    fun `a dot beside the number stays inside its cell at the least width, and a mark with no room is logged`() {
        val leastPx = scale.px(LEAST_DP - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val column = leastPx / 7f
        val sunday = monday.plusDays(6)
        val special = Probe.allDay(sunday).copy(kind = Kinds.SPECIAL)
        val marked = Probe.week(monday, mapOf(6 to Probe.day(sunday, marks = listOf(special))))
        val plain = rows.render(Probe.week(monday), leastPx, scale, SampleData.dark)
        val bitmap = rows.render(marked, leastPx, scale, SampleData.dark)
        val changed = (0 until bitmap.width).filter { x -> differs(bitmap, plain, x) }
        assertTrue("no dot", changed.isNotEmpty())
        val right = 7 * column - scale.px(PhoneMonth.CELL_SIDE)
        assertTrue("the dot reaches x ${changed.last()}, its cell ends at $right", changed.last() <= right.toInt())
        assertTrue(ShadowLog.getLogsForTag("WeekRowRenderer").isEmpty())
        val tinyPx = scale.px(TINY_DP - 2 * PhoneMonth.CARD_INSET).roundToInt()
        rows.render(marked, tinyPx, scale, SampleData.dark)
        assertEquals(
            listOf("$sunday: no room beside the number for 1 of its marks"),
            ShadowLog.getLogsForTag("WeekRowRenderer").map { it.msg },
        )
    }

    /** Whether any pixel of column x differs between the two bitmaps. */
    private fun differs(
        a: Bitmap,
        b: Bitmap,
        x: Int,
    ) = (0 until a.height).any { y -> a.getPixel(x, y) != b.getPixel(x, y) }

    private fun assertLantern(
        bitmap: Bitmap,
        left: Float,
        top: Float,
        size: Float,
    ) {
        val pixel = bitmap.at(Probe.lanternPoint(left, size, LANTERN_X), Probe.lanternPoint(top, size, LANTERN_Y))
        assertTrue("${Integer.toHexString(pixel)} is not the lantern's body", Probe.near(pixel, Probe.LANTERN_BODY))
    }

    private fun render(entry: Entry): Bitmap {
        val week = Probe.week(monday, mapOf(0 to Probe.day(monday, shown = listOf(entry))))
        return rows.render(week, Probe.widthPx, scale, SampleData.dark)
    }

    private fun renderMark(entry: Entry): Bitmap {
        val special = entry.copy(kind = Kinds.SPECIAL)
        val week = Probe.week(monday, mapOf(0 to Probe.day(monday, marks = listOf(special))))
        return rows.render(week, widePx, scale, SampleData.dark)
    }

    private companion object {
        const val MARK_CENTRE = 12f
        const val CHEVRON_FILLED = 14.5f
        const val LANTERN_X = 21.5f
        const val LANTERN_Y = 25f
        const val WIDE_DP = 349

        /** The least width of the month and "Woche quer", 250 dp (D34), and one far below it. */
        const val LEAST_DP = 250
        const val TINY_DP = 150
    }
}
