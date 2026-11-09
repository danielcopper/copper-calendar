package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.month.MonthView
import dev.cupr.calendar.sample.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import kotlin.math.ceil

/**
 * Renders every week row of the sample month, dark and light, to PNG files
 * (on the card's colour, as the widget shows them) under build/render, at
 * the density of a Pixel 6 and the width of a 4-cell-wide widget.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekRowRenderTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets
    private val fonts = Fonts(assets)
    private val icons = Icons(assets)
    private val rows = WeekRowRenderer(fonts, icons, Drawings(assets))
    private val chrome = ChromeRenderer(fonts, icons)
    private val scale = Scale(DENSITY)
    private val widthPx = (CONTENT_WIDTH * DENSITY).toInt()
    private val view: MonthView =
        MonthLayout.build(SampleData.month, SampleData.entries, LocalDateTime.now(SampleData.clock))
    private val output = File(System.getProperty("render.dir") ?: "build/render")
    private val modes = listOf("dark" to SampleData.dark, "light" to SampleData.light)

    @Test
    fun `renders every week row of the sample month, dark and light, to PNG files`() {
        output.mkdirs()
        var bytes = 0L
        for ((mode, palette) in modes) {
            view.weeks.forEachIndexed { index, week ->
                val bitmap = rows.render(week, widthPx, scale, palette)
                assertEquals(widthPx, bitmap.width)
                assertEquals(ceil(scale.hairline + scale.px(rows.heightDp(week))).toInt(), bitmap.height)
                bytes += bitmap.allocationByteCount
                write(bitmap, palette, "week-$index-$mode.png")
            }
            val header = chrome.header(SampleData.month, widthPx, scale, palette)
            val fab = chrome.fab(scale, palette)
            bytes += header.allocationByteCount + fab.allocationByteCount
            write(header, palette, "header-$mode.png")
            write(fab, palette, "fab-$mode.png")
        }
        val pngs = output.listFiles { file -> file.extension == "png" }.orEmpty()
        assertEquals(2 * (view.weeks.size + 2), pngs.size)
        val budget =
            "bitmap: ${view.weeks.size} weeks x 2 modes + header and fab x 2 at ${widthPx}px wide, " +
                "density $DENSITY: $bytes bytes"
        File(output, "budget.txt").writeText(budget + "\n")
        println(budget)
        pngs.sorted().forEach { println("render: ${it.path}") }
    }

    @Test
    fun `the rows of the sample month are as tall as their tallest cell, at least the cell's least height`() {
        // 66 dp, and 79 dp for the weeks with a bar lane over two lines.
        val heights = view.weeks.map { rows.heightDp(it) }
        assertEquals(listOf(66f, 66f, 66f, 66f, 79f, 66f), heights)
    }

    @Test
    fun `today's number stands in the accent's pill, in each palette`() {
        val column = widthPx / 7f
        val x = 2 * column + scale.hairline + scale.px(2f + 1.5f)
        val y = scale.hairline + scale.px(3f + 11f)
        for ((_, palette) in modes) {
            val bitmap = rows.render(view.weeks[view.todayWeek], widthPx, scale, palette)
            assertEquals(palette.accent, bitmap.getPixel(x.toInt(), y.toInt()))
        }
    }

    @Test
    fun `plus is the accent's circle, in each palette`() {
        // Inside the circle, left of the plus, at the circle's middle height.
        val x = scale.px(ChromeRenderer.FAB_MARGIN + 6f)
        val y = scale.px(ChromeRenderer.FAB_MARGIN + ChromeRenderer.FAB_SIZE / 2)
        for ((_, palette) in modes) {
            assertEquals(palette.accent, chrome.fab(scale, palette).getPixel(x.toInt(), y.toInt()))
        }
    }

    @Test
    fun `a bar is drawn in its entry's colour across the days it covers`() {
        val palette = SampleData.light
        val bitmap = rows.render(view.weeks[3], widthPx, scale, palette)
        val column = widthPx / 7f
        // The inset band at the start of "Kurzurlaub Ostsee" on Friday.
        val x = 4 * column + scale.px(3f + 1.5f)
        val y = scale.hairline + scale.px(3f + 22f + 6f)
        assertEquals(0xFF663399.toInt(), bitmap.getPixel(x.toInt(), y.toInt()))
    }

    @Test
    fun `the header draws in the palette it is given`() {
        val dark = chrome.header(SampleData.month, widthPx, scale, SampleData.dark)
        val light = chrome.header(SampleData.month, widthPx, scale, SampleData.light)
        assertFalse(dark.sameAs(light))
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
        File(output, name).outputStream().use { onCard.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    private companion object {
        /** A Pixel 6 (411 dpi, density 2.625). */
        const val DENSITY = 2.625f

        /** A 4-cell-wide widget (276 dp) less the card's padding and border on both sides. */
        const val CONTENT_WIDTH = 276f - 2 * (12f + 1f)

        const val PNG_QUALITY = 100
    }
}
