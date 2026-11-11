package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.month.MonthView
import dev.cupr.calendar.render.Probe.at
import dev.cupr.calendar.render.Probe.has
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.week.WeekLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Renders every week row of the sample month, dark and light, to PNG files
 * (on the card's colour, as the widget shows them) under build/render, at a
 * Pixel 6's density and the width of a 4-cell-wide widget ([Probe]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekRowRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = context.assets
    private val fonts = Fonts(assets)
    private val icons = Icons(assets)
    private val rows = WeekRowRenderer(fonts, icons, Drawings(assets))
    private val weekdays = context.resources.getStringArray(R.array.weekday_heads).toList()
    private val chrome = ChromeRenderer(fonts, icons, weekdays)
    private val scale = Probe.scale
    private val widthPx = Probe.widthPx
    private val view: MonthView =
        MonthLayout.build(SampleData.month, SampleData.entries, LocalDate.now(SampleData.clock))
    private val output = Probe.renderDir
    private val modes = listOf("dark" to SampleData.dark, "light" to SampleData.light)

    /** The files the render writes: a week row, the header and "+" per mode, and the bitmaps' size. */
    private val written =
        modes.flatMap { (mode, _) ->
            view.weeks.indices.map { "week-$it-$mode.png" } + listOf("header-$mode.png", "fab-$mode.png")
        } + BUDGET

    @Test
    fun `renders every week row of the sample month, dark and light, to PNG files`() {
        // Only this test's own files, so a render.dir pointed elsewhere loses nothing else.
        output.mkdirs()
        written.forEach { File(output, it).delete() }
        var bytes = 0L
        for ((mode, palette) in modes) {
            val heights =
                view.weeks.mapIndexed { index, week ->
                    val bitmap = rows.render(week, widthPx, scale, palette)
                    bytes += bitmap.allocationByteCount
                    write(bitmap, palette, "week-$index-$mode.png")
                    bitmap.height
                }
            // 66 dp plus the 2 px divider is 176 px; the week of 23 November, 79 dp, is 210 px.
            assertEquals(listOf(176, 176, 176, 176, 210, 176), heights)
            val header = chrome.header(SampleData.month, widthPx, scale, palette)
            val fab = chrome.fab(scale, palette)
            bytes += header.allocationByteCount + fab.allocationByteCount
            write(header, palette, "header-$mode.png")
            write(fab, palette, "fab-$mode.png")
        }
        val budget =
            "bitmap: ${view.weeks.size} weeks x 2 modes + header and fab x 2 at ${widthPx}px wide, " +
                "density ${Probe.DENSITY}: $bytes bytes"
        File(output, BUDGET).writeText(budget + "\n")
        println(budget)
        assertEquals("missing files", emptyList<String>(), written.filterNot { File(output, it).isFile })
        assertTrue("$bytes bytes are more than half the bitmap budget", bytes <= BITMAP_BUDGET / 2)
    }

    @Test
    fun `the rows of the sample month are as tall as their tallest cell, at least the cell's least height`() {
        // 66 dp, and 79 dp for the week whose bar lane sits above two lines.
        val heights = view.weeks.map { rows.heightDp(it) }
        assertEquals(listOf(66f, 66f, 66f, 66f, 79f, 66f), heights)
    }

    @Test
    fun `today's number stands in the accent's pill, in each palette`() {
        val x = Probe.cellX(2) + scale.px(1.5f)
        val y = scale.hairline + scale.px(PhoneMonth.CELL_TOP + PhoneMonth.NUMBER_ROW / 2)
        for ((_, palette) in modes) {
            val bitmap = rows.render(view.weeks[view.todayWeek], widthPx, scale, palette)
            assertEquals(palette.accent, bitmap.at(x, y))
        }
    }

    @Test
    fun `plus is the accent's circle, in each palette`() {
        // Inside the circle, left of the plus, at the circle's middle height.
        val x = scale.px(PhoneMonth.FAB_MARGIN + 6f)
        val y = scale.px(PhoneMonth.FAB_MARGIN + PhoneMonth.FAB_SIZE / 2)
        for ((_, palette) in modes) {
            assertEquals(palette.accent, chrome.fab(scale, palette).at(x, y))
        }
    }

    @Test
    fun `a bar is drawn in its entry's colour across the days it covers`() {
        val bitmap = rows.render(view.weeks[3], widthPx, scale, SampleData.light)
        // The band at the start of "Kurzurlaub Ostsee" on Friday.
        val x = Probe.columnX(4) + scale.px(PhoneMonth.BAR_INSET + PhoneMonth.BAR_BAND / 2)
        assertEquals(0xFF663399.toInt(), bitmap.at(x, Probe.laneMiddle(0)))
    }

    @Test
    fun `the header draws its title, chevron and weekday heads in the palette it is given`() {
        val rowBottom = scale.px(PhoneMonth.TITLE_TOP + PhoneMonth.TITLE_ROW)
        val chevronLeft = widthPx - scale.px(PhoneMonth.CHEVRON)
        for ((_, palette) in modes) {
            val header = chrome.header(SampleData.month, widthPx, scale, palette)
            assertTrue("title", header.has(palette.secondaryText, 0f, 0f, chevronLeft, rowBottom))
            assertTrue("chevron", header.has(palette.tertiaryText, chevronLeft, 0f, widthPx.toFloat(), rowBottom))
            assertTrue(
                "heads",
                header.has(palette.tertiaryText, 0f, rowBottom, widthPx.toFloat(), header.height.toFloat()),
            )
        }
    }

    @Test
    fun `the weekday heads are the string resource's, Monday to Sunday`() {
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), weekdays)
    }

    @Test
    fun `the header draws the weekday heads it is given, and needs seven`() {
        val given = chrome.header(SampleData.month, widthPx, scale, SampleData.dark)
        val other = ChromeRenderer(fonts, icons, listOf("A", "B", "C", "D", "E", "F", "G"))
        assertFalse(given.sameAs(other.header(SampleData.month, widthPx, scale, SampleData.dark)))
        val error =
            assertThrows(IllegalArgumentException::class.java) { ChromeRenderer(fonts, icons, weekdays.drop(1)) }
        assertEquals("The header needs 7 weekday heads, not 6", error.message)
    }

    @Test
    fun `a week's range across two months shows whole at 150 dp, and narrower is cut, never left of the header`() {
        val range = WeekLayout.range(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 6))
        assertEquals("30. Nov. – 6. Dez.", range)
        val placed = { widthDp: Int ->
            chrome.range(range, scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt(), scale)
        }
        val (whole, wholeX) = placed(150)
        assertEquals(range, whole)
        assertTrue("$wholeX", wholeX >= 0f)
        for (widthDp in listOf(110, 100)) {
            val (cut, x) = placed(widthDp)
            assertTrue("$widthDp dp: $cut", cut.endsWith("…") && cut.length < range.length)
            assertTrue("$widthDp dp: $x", x >= 0f)
        }
    }

    private fun write(
        bitmap: Bitmap,
        palette: Palette,
        name: String,
    ) {
        val onCard = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        // The same density as the row's, so drawing it copies pixels instead of scaling them.
        onCard.density = bitmap.density
        Canvas(onCard).apply {
            drawColor(palette.card)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        File(output, name).writeBytes(Probe.png(onCard))
    }

    private companion object {
        const val BUDGET = "budget.txt"

        /** Android 17's limit for a widget's bitmaps, 6 × width × height bytes, at 1080 × 2400 (research.md, 6). */
        const val BITMAP_BUDGET = 6L * 1080 * 2400
    }
}
