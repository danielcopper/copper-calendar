package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Renders the week's rows ("Woche") of the sample week, Monday to Sunday and
 * from today, dark and light, to PNG files under build/render: each as its
 * header above its rows on the card's colour, as the widget shows them, at
 * a Pixel 6's density and the width of a 4-cell-wide widget ([Probe]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayRowRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = context.assets
    private val fonts = Fonts(assets)
    private val icons = Icons(assets)
    private val rows = DayRowRenderer(fonts, icons, Drawings(assets), WeekTexts.of(context.resources))
    private val chrome = ChromeRenderer(fonts, icons, context.resources.getStringArray(R.array.weekday_heads).toList())
    private val scale = Probe.scale
    private val today = LocalDate.now(SampleData.clock)
    private val modes = listOf("dark" to SampleData.dark, "light" to SampleData.light)
    private val variants = listOf("monday" to WeekDays.MONDAY_TO_SUNDAY, "today" to WeekDays.FROM_TODAY)

    /** The rows' width: the card inside its border. */
    private val rowPx = scale.px(Probe.WIDTH_DP - 2 * PhoneMonth.CARD_BORDER).roundToInt()

    @Test
    fun `renders the sample week's rows, Monday to Sunday and from today, dark and light, to PNG files`() {
        val output = Probe.renderDir
        output.mkdirs()
        for ((variant, days) in variants) {
            val dates = days.dates(today)
            val week = WeekLayout.build(dates, SampleData.entries, today)
            for ((mode, palette) in modes) {
                val name = "woche-$variant-$mode.png"
                File(output, name).delete()
                val header =
                    chrome.weekHeader(
                        context.getString(days.title),
                        WeekLayout.range(dates.first(), dates.last()),
                        null,
                        Probe.widthPx,
                        scale,
                        palette,
                    )
                val drawn = week.map { rows.render(it, rowPx, scale, palette) }
                write(header, drawn, palette, File(output, name))
                assertEquals(7, drawn.size)
            }
        }
    }

    /** The header above the rows, on the card's colour, as the widget shows them inside its border and padding. */
    private fun write(
        header: Bitmap,
        drawn: List<Bitmap>,
        palette: Palette,
        file: File,
    ) {
        val side = scale.px(PhoneMonth.CARD_SIDE)
        val width = rowPx
        val height = header.height + drawn.sumOf { it.height }
        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        sheet.density = header.density
        Canvas(sheet).apply {
            drawColor(palette.card)
            drawBitmap(header, side, 0f, null)
            var y = header.height.toFloat()
            for (row in drawn) {
                drawBitmap(row, 0f, y, null)
                y += row.height
            }
        }
        file.writeBytes(Probe.png(sheet))
    }
}
