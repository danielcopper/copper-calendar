package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.render.Probe.has
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.week.WeekDay
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                write(header, drawn, palette, File(output, name), rowPx)
                assertEquals(7, drawn.size)
            }
        }
    }

    /** At 2 cells wide (130 dp, design.md's table), narrower than "Woche" may be made, to judge whether it reads. */
    @Test
    fun `renders the sample week's rows at 2 cells wide, dark and light, to PNG files`() {
        val widthDp = 130
        val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val narrowPx = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
        val dates = WeekDays.MONDAY_TO_SUNDAY.dates(today)
        val week = WeekLayout.build(dates, SampleData.entries, today)
        Probe.renderDir.mkdirs()
        for ((mode, palette) in modes) {
            val file = File(Probe.renderDir, "woche-2cells-monday-$mode.png")
            file.delete()
            val title = context.getString(WeekDays.MONDAY_TO_SUNDAY.title)
            val header =
                chrome.weekHeader(
                    title,
                    WeekLayout.range(dates.first(), dates.last()),
                    null,
                    headerPx,
                    scale,
                    palette,
                )
            val drawn = week.map { rows.render(it, narrowPx, scale, palette) }
            write(header, drawn, palette, file, narrowPx)
            assertTrue(file.isFile)
        }
    }

    /** A day of the sample week with entries as given, the others empty. */
    private fun day(
        date: LocalDate,
        vararg entries: Entry,
    ): WeekDay = WeekLayout.build(listOf(date), entries.toList(), today).single()

    private fun chip(
        first: LocalDate,
        days: Long = 1,
        kind: String? = null,
    ) = Probe.allDay(first).copy(end = first.plusDays(days).atStartOfDay(), kind = kind)

    @Test
    fun `an entry's tap area lies over its line, one line lower below the titles running on from an earlier day`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val plain = rows.entryAreas(day(thursday, chip(thursday)), rowPx, scale).single()
        assertEquals(scale.hairline + scale.px(TileWeek.ROW_PAD), plain.top, 0.01f)
        assertEquals(scale.px(PhoneMonth.CARD_SIDE + TileWeek.DAY_COLUMN + TileWeek.COLUMN_GAP), plain.left, 0.01f)
        assertEquals(rowPx - scale.px(PhoneMonth.CARD_SIDE), plain.right, 0.01f)
        val week =
            WeekLayout.build(
                listOf(thursday.minusDays(1), thursday),
                listOf(chip(thursday.minusDays(1), 2), chip(thursday)),
                today,
            )
        assertEquals(listOf("Ganztags"), week[1].running)
        val below = rows.entryAreas(week[1], rowPx, scale).single()
        assertEquals(plain.top + scale.px(TileWeek.LINE + TileWeek.LINE_GAP), below.top, 0.01f)
    }

    @Test
    fun `today's row is tinted in the soft accent where it begins, a past row is drawn at half opacity`() {
        val x = scale.px(PhoneMonth.CARD_SIDE + 2f)
        for ((_, palette) in modes) {
            val today = rows.render(day(today), rowPx, scale, palette)
            val tint = today.getPixel(x.toInt(), today.height - 3)
            assertTrue(
                Integer.toHexString(tint),
                Probe.near(palette.card.blend(tint), palette.card.blend(palette.accentSoft)),
            )
        }
        val monday = LocalDate.of(2026, 11, 9)
        val past = rows.render(day(monday, chip(monday)), rowPx, scale, SampleData.dark)
        val barX = scale.px(PhoneMonth.CARD_SIDE + TileWeek.DAY_COLUMN + TileWeek.COLUMN_GAP + 1f)
        val barY = scale.hairline + scale.px(TileWeek.ROW_PAD + 10f)
        val bar = past.getPixel(barX.toInt(), barY.toInt())
        assertEquals(128f, Color.alpha(bar).toFloat(), 2f)
    }

    @Test
    fun `a public holiday's date is red`() {
        val wednesday = LocalDate.of(2026, 11, 18)
        val holiday = rows.render(day(wednesday, chip(wednesday, kind = Kinds.HOLIDAY)), rowPx, scale, SampleData.light)
        val plain = rows.render(day(wednesday, chip(wednesday)), rowPx, scale, SampleData.light)
        val head = { bitmap: Bitmap ->
            bitmap.has(
                SampleData.light.holiday,
                0f,
                0f,
                scale.px(PhoneMonth.CARD_SIDE + TileWeek.DAY_COLUMN),
                bitmap.height.toFloat(),
            )
        }
        assertTrue(head(holiday))
        assertFalse(head(plain))
    }

    /** This colour drawn over an opaque one. */
    private fun Int.blend(over: Int): Int {
        val a = Color.alpha(over) / 255f
        val mix = { c: (Int) -> Int -> (c(over) * a + c(this) * (1 - a)).roundToInt() }
        return Color.rgb(mix(Color::red), mix(Color::green), mix(Color::blue))
    }

    /** The header above the rows, on the card's colour, as the widget shows them inside its border, width px wide. */
    private fun write(
        header: Bitmap,
        drawn: List<Bitmap>,
        palette: Palette,
        file: File,
        width: Int,
    ) {
        val side = scale.px(PhoneMonth.CARD_SIDE)
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
