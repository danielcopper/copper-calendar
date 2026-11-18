package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.month.Bar
import dev.cupr.calendar.render.Probe.at
import dev.cupr.calendar.render.Probe.has
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a week row draws its cells and bars: opacities, the weekend tint, "+N", lanes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CellRenderTest {
    private val rows = Probe.rows()
    private val monday = LocalDate.of(2026, 11, 16)
    private val palette = SampleData.dark

    private fun chip(date: LocalDate) = Probe.allDay(date)

    /** The "+N" line below one shown entry in Monday's cell, as its own bitmap. */
    private fun moreLine(more: Int): Bitmap {
        val bitmap = render(0 to Probe.day(monday, shown = listOf(chip(monday)), more = more))
        val top = Probe.moreTop(lanes = 1).toInt()
        val width = (Probe.columnX(1) - Probe.cellX(0)).toInt()
        val height = Probe.scale.px(PhoneMonth.MORE_LINE).toInt()
        return Bitmap.createBitmap(bitmap, Probe.cellX(0).toInt(), top, width, height)
    }

    /** The alpha of the opaque band at the left of Monday's chip. */
    private fun bandAlpha(bitmap: Bitmap) =
        Color.alpha(bitmap.at(Probe.cellX(0) + Probe.scale.px(PhoneMonth.CHIP_BAND / 2), Probe.laneMiddle(0)))

    private fun render(vararg days: Pair<Int, dev.cupr.calendar.month.Day>): Bitmap =
        rows.render(Probe.week(monday, days.toMap()), Probe.widthPx, Probe.scale, palette)

    private fun assertAlpha(
        opacity: Float,
        alpha: Int,
    ) {
        assertTrue("alpha $alpha is not ${(opacity * 255).roundToInt()}", abs(alpha - opacity * 255) <= 1.5f)
    }

    @Test
    fun `a day of another month is drawn at its opacity`() {
        assertEquals(255, bandAlpha(render(0 to Probe.day(monday, shown = listOf(chip(monday))))))
        val faint = render(0 to Probe.day(monday, shown = listOf(chip(monday)), otherMonth = true))
        assertAlpha(PhoneMonth.OTHER_MONTH, bandAlpha(faint))
    }

    @Test
    fun `a past day's entries are drawn at their opacity`() {
        val past = render(0 to Probe.day(monday, shown = listOf(chip(monday)), past = true))
        assertAlpha(PhoneMonth.PAST_ENTRY, bandAlpha(past))
    }

    @Test
    fun `an entry's line is as tall as a bar's, from the top of its lane`() {
        val bitmap = render(0 to Probe.day(monday, shown = listOf(chip(monday))))
        // Past the rounded corner, where the chip's tint runs from its top to its bottom.
        val x = (Probe.cellX(0) + Probe.scale.px(PhoneMonth.ENTRY_RADIUS) + 1).toInt()
        // Below the number row's middle, past the week's divider.
        val below = Probe.scale.px(PhoneMonth.CELL_TOP + PhoneMonth.NUMBER_ROW / 2).toInt()
        val chip = (below until bitmap.height).filter { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
        // Its top and bottom edges fall inside pixel rows, which they cover in part.
        val top = Probe.laneTop(0)
        assertEquals(top.toInt(), chip.first())
        assertEquals((top + Probe.scale.px(PhoneMonth.LANE_LINE)).toInt(), chip.last())
        assertEquals(chip.last() - chip.first() + 1, chip.size)
    }

    @Test
    fun `an entry in the lanes is dimmed as its day is, a bar over the same days is not`() {
        val tuesday = monday.plusDays(1)
        val wednesday = monday.plusDays(2)
        val long = chip(monday).copy(title = "Lang", end = monday.plusDays(3).atStartOfDay())
        val bar = Bar(long, from = 0, to = 2, lane = 0, opens = true, closes = true)
        val days =
            mapOf(
                0 to Probe.day(monday, shown = listOf(chip(monday)), otherMonth = true),
                1 to Probe.day(tuesday, shown = listOf(chip(tuesday)), past = true),
                2 to Probe.day(wednesday, shown = listOf(chip(wednesday))),
            )
        val bitmap = rows.render(Probe.week(monday, days, listOf(bar)), Probe.widthPx, Probe.scale, palette)
        val alphaIn = { column: Int ->
            Color.alpha(bitmap.at(Probe.cellX(column) + Probe.scale.px(PhoneMonth.CHIP_BAND / 2), Probe.laneMiddle(1)))
        }
        assertAlpha(PhoneMonth.OTHER_MONTH, alphaIn(0))
        assertAlpha(PhoneMonth.PAST_ENTRY, alphaIn(1))
        assertEquals(255, alphaIn(2))
        // The bar's band where it begins, over the day of another month.
        val barBand = Probe.columnX(0) + Probe.scale.px(PhoneMonth.BAR_INSET + PhoneMonth.BAR_BAND / 2)
        assertEquals(255, Color.alpha(bitmap.at(barBand, Probe.laneMiddle(0))))
    }

    @Test
    fun `a week's cells are as tall as its lanes and +N`() {
        val four = List(4) { chip(monday) }
        val lanes = Probe.week(monday, mapOf(0 to Probe.day(monday, shown = four)))
        // 3 + 22 + 2 + 4 × 15 + 4 dp.
        assertEquals(91f, rows.heightDp(lanes), 0.01f)
        val more =
            Probe.week(
                monday,
                mapOf(
                    0 to Probe.day(monday, shown = four),
                    1 to Probe.day(monday.plusDays(1), more = 1),
                ),
            )
        // And 2 + 14.5 dp for "+N" below the lanes.
        assertEquals(107.5f, rows.heightDp(more), 0.01f)
    }

    @Test
    fun `the weekend's cells are tinted, the others not`() {
        val bitmap = render()
        val bottom = bitmap.height - 2f
        assertEquals(0, Color.alpha(bitmap.at(Probe.cellX(4) + 4, bottom)))
        assertAlpha(PhoneMonth.WEEKEND_TINT, Color.alpha(bitmap.at(Probe.cellX(6) + 4, bottom)))
    }

    @Test
    fun `+N is drawn below the week's lanes`() {
        val top = Probe.moreTop(lanes = 1)
        val bottom = top + Probe.scale.px(PhoneMonth.MORE_LINE)
        val right = Probe.cellX(0) + Probe.scale.px(20f)
        val none = render(0 to Probe.day(monday, shown = listOf(chip(monday))))
        assertFalse(none.has(palette.text, Probe.cellX(0), top, right, bottom))
        val more = render(0 to Probe.day(monday, shown = listOf(chip(monday)), more = 2))
        assertTrue(more.has(palette.text, Probe.cellX(0), top, right, bottom))
        // It says how many: "+2" and "+3" differ.
        assertFalse(moreLine(2).sameAs(moreLine(3)))
    }

    @Test
    fun `a public holiday's number is drawn in the holiday colour`() {
        val top = Probe.scale.hairline + Probe.scale.px(PhoneMonth.CELL_TOP)
        val bottom = top + Probe.scale.px(PhoneMonth.NUMBER_ROW)
        val right = Probe.cellX(0) + Probe.scale.px(PhoneMonth.NUMBER_MIN_WIDTH)
        val plain = render()
        assertFalse(plain.has(palette.holiday, Probe.cellX(0), top, right, bottom))
        val holiday = render(0 to Probe.day(monday, holiday = true))
        assertTrue(holiday.has(palette.holiday, Probe.cellX(0), top, right, bottom))
    }

    @Test
    fun `bars are drawn in their lanes up to their last column`() {
        val long = chip(monday).copy(title = "Lang", end = monday.plusDays(7).atStartOfDay())
        val short = chip(monday).copy(title = "Kurz", end = monday.plusDays(3).atStartOfDay())
        val bars =
            listOf(
                Bar(long, from = 0, to = 6, lane = 0, opens = true, closes = true),
                Bar(short, from = 0, to = 2, lane = 1, opens = true, closes = true),
            )
        val bitmap = rows.render(Probe.week(monday, bars = bars), Probe.widthPx, Probe.scale, palette)
        // The tinted body: in lane 1 at its middle column, in lane 0 in the last column.
        assertAlpha(
            PhoneMonth.BAR_TINT,
            Color.alpha(bitmap.at(Probe.columnX(1) + Probe.scale.px(12f), Probe.laneMiddle(1))),
        )
        // Sunday is tinted under the bar: the bar's opacity over the weekend tint's.
        val overTint = PhoneMonth.BAR_TINT + PhoneMonth.WEEKEND_TINT * (1 - PhoneMonth.BAR_TINT)
        assertAlpha(overTint, Color.alpha(bitmap.at(Probe.columnX(6) + Probe.scale.px(4f), Probe.laneMiddle(0))))
        // Lane 1 ends with its third column, its rounded end inset from the column's edge.
        assertEquals(0, Color.alpha(bitmap.at(Probe.columnX(3) + Probe.scale.px(12f), Probe.laneMiddle(1))))
        assertEquals(
            0,
            Color.alpha(bitmap.at(Probe.columnX(3) - Probe.scale.px(PhoneMonth.BAR_INSET / 2), Probe.laneMiddle(1))),
        )
    }
}
