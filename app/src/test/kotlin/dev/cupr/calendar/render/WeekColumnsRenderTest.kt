package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import dev.cupr.calendar.widget.WeekColumnsWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Renders "Woche quer" of the sample week, Monday to Sunday and from today,
 * dark and light, to PNG files under build/render: its header above its one
 * drawn row on the card's colour, the row whole, as far as the list scrolls,
 * at a Pixel 6's density and the width of a 4-cell-wide widget ([Probe]).
 * Also measures both week widgets' bitmaps against Android's limit
 * (budget-week.txt).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekColumnsRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = context.assets
    private val fonts = Fonts(assets)
    private val icons = Icons(assets)
    private val drawings = Drawings(assets)
    private val rows = WeekRowRenderer(fonts, icons, drawings)
    private val dayRows = DayRowRenderer(fonts, icons, drawings, WeekTexts.of(context.resources))
    private val stackedRows = DayRowRenderer(fonts, icons, drawings, WeekTexts.of(context.resources), stacked = true)
    private val chrome = ChromeRenderer(fonts, icons, context.resources.getStringArray(R.array.weekday_heads).toList())
    private val scale = Probe.scale
    private val today = LocalDate.now(SampleData.clock)
    private val modes = listOf("dark" to SampleData.dark, "light" to SampleData.light)
    private val variants = listOf("monday" to WeekDays.MONDAY_TO_SUNDAY, "today" to WeekDays.FROM_TODAY)

    @Test
    fun `renders the sample week's columns, Monday to Sunday and from today, dark and light, to PNG files`() {
        val output = Probe.renderDir
        output.mkdirs()
        for ((variant, days) in variants) {
            val dates = days.dates(today)
            val week = MonthLayout.week(dates, SampleData.entries, today)
            for ((mode, palette) in modes) {
                val name = "woche-quer-$variant-$mode.png"
                File(output, name).delete()
                val header = header(days, dates, Probe.widthPx, palette)
                val row = rows.render(week, Probe.widthPx, scale, palette)
                write(header, row, palette, File(output, name))
            }
        }
        // Saturday's eight entries: 3 + 22 + 2 + 8 × 16.5 + 4 dp.
        val monday = MonthLayout.week(WeekDays.MONDAY_TO_SUNDAY.dates(today), SampleData.entries, today)
        assertEquals(163f, rows.heightDp(monday), 0.01f)
    }

    /**
     * Both week widgets' bitmaps (header, "+" and rows, light and dark) for
     * the sample week and for a week with a very full Saturday, at the
     * default and a full-width size, and "Woche" also stacked at its least
     * width and 3 cells wide, against Android 17's limit at a Pixel 6's
     * screen ([BITMAP_LIMIT]), as drawn without the cap.
     */
    @Test
    fun `measures the week widgets' bitmaps against Android's limit`() {
        val dates = WeekDays.MONDAY_TO_SUNDAY.dates(today)
        val saturday = dates[5]
        val full = SampleData.entries + List(FULL_DAY) { fullDayEntry(saturday, it) }
        val lines = mutableListOf<String>()
        val sample = mutableListOf<Long>()
        for ((label, entries) in listOf("sample week" to SampleData.entries, "$FULL_DAY more on Saturday" to full)) {
            for (widthDp in listOf(DEFAULT_DP, LARGE_DP)) {
                val woche = wocheBytes(dates, entries, widthDp)
                val quer = querBytes(dates, entries, widthDp)
                lines += "Woche, $label, $widthDp dp: $woche bytes, ${percent(woche)} % of the limit"
                lines += "Woche quer, $label, $widthDp dp: $quer bytes, ${percent(quer)} % of the limit"
                if (entries === SampleData.entries) sample += listOf(woche, quer)
            }
            for (widthDp in listOf(LEAST_DP, THREE_CELLS_DP)) {
                val woche = wocheBytes(dates, entries, widthDp)
                lines += "Woche, $label, $widthDp dp, stacked: $woche bytes, ${percent(woche)} % of the limit"
                if (entries === SampleData.entries) sample += woche
            }
        }
        val text =
            "density ${Probe.DENSITY}, limit 6 x 1080 x 2400 = $BITMAP_LIMIT bytes\n" + lines.joinToString("\n") + "\n"
        Probe.renderDir.mkdirs()
        File(Probe.renderDir, "budget-week.txt").writeText(text)
        println(text)
        assertTrue("the sample week passes the limit:\n$text", sample.all { it < BITMAP_LIMIT })
    }

    private fun wocheBytes(
        dates: List<LocalDate>,
        entries: List<Entry>,
        widthDp: Int,
    ): Long {
        val week = WeekLayout.build(dates, entries, today)
        val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val rowPx = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
        val renderer = if (DayRowRenderer.stacksAt(widthDp)) stackedRows else dayRows
        return modes.sumOf { (_, palette) ->
            chrome.weekHeader("Diese Woche", "9.–15. Nov.", null, headerPx, scale, palette).allocationByteCount +
                chrome.fab(scale, palette).allocationByteCount +
                week.sumOf { renderer.render(it, rowPx, scale, palette).allocationByteCount }.toLong()
        }
    }

    private fun querBytes(
        dates: List<LocalDate>,
        entries: List<Entry>,
        widthDp: Int,
    ): Long {
        val week = MonthLayout.week(dates, entries, today)
        val widthPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        return modes.sumOf { (_, palette) ->
            val header =
                chrome.weekHeader(
                    "Diese Woche",
                    "9.–15. Nov.",
                    dates.first().dayOfWeek,
                    widthPx,
                    scale,
                    palette,
                )
            // The row as tall as a widget too low for it draws it: with the fade's blank room at its end.
            header.allocationByteCount.toLong() + chrome.fab(scale, palette).allocationByteCount +
                rows.render(week, widthPx, scale, palette, WeekColumnsWidget.FADE_DP.toFloat()).allocationByteCount
        }
    }

    private fun fullDayEntry(
        day: LocalDate,
        index: Int,
    ) = Entry(
        start = day.atTime(6, 0).plusMinutes(index * 30L),
        end = day.atTime(6, 30).plusMinutes(index * 30L),
        allDay = false,
        title = "Termin ${index + 1}",
        calendar = SampleData.FAMILY,
        icon = "mdi:soccer",
        uid = "full-$index",
    )

    private fun percent(bytes: Long) = (bytes * 100.0 / BITMAP_LIMIT).roundToInt()

    private fun header(
        days: WeekDays,
        dates: List<LocalDate>,
        widthPx: Int,
        palette: Palette,
    ) = chrome.weekHeader(
        context.getString(days.title),
        WeekLayout.range(dates.first(), dates.last()),
        dates.first().dayOfWeek,
        widthPx,
        scale,
        palette,
    )

    /** The header above the row, on the card's colour. */
    private fun write(
        header: Bitmap,
        row: Bitmap,
        palette: Palette,
        file: File,
    ) {
        val sheet = Bitmap.createBitmap(header.width, header.height + row.height, Bitmap.Config.ARGB_8888)
        sheet.density = header.density
        Canvas(sheet).apply {
            drawColor(palette.card)
            drawBitmap(header, 0f, 0f, null)
            drawBitmap(row, 0f, header.height.toFloat(), null)
        }
        file.writeBytes(Probe.png(sheet))
    }

    private companion object {
        /** A very full day: this many more entries on Saturday. */
        const val FULL_DAY = 30

        const val DEFAULT_DP = 276

        /** "Woche" at its least width (D34) and 3 cells wide (Android's cell-size table), where its rows stack. */
        const val LEAST_DP = 110
        const val THREE_CELLS_DP = 203

        /** A widget as wide as a Pixel 6's screen (412 dp). */
        const val LARGE_DP = 412

        /** Android 17's limit for a widget's bitmaps, 6 × width × height bytes, at 1080 × 2400 (research.md, 6). */
        const val BITMAP_LIMIT = 6L * 1080 * 2400
    }
}
