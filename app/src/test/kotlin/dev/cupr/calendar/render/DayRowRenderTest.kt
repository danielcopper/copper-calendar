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
import java.util.Locale
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
    private val texts = WeekTexts.of(context.resources)
    private val rows = DayRowRenderer(fonts, icons, Drawings(assets), texts)
    private val stacked = DayRowRenderer(fonts, icons, Drawings(assets), texts, stacked = true)
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

    /**
     * From the least width, 150 dp, to 4 cells (276 dp), stacked below 250 dp
     * as the widget draws them, on both sides of that switch.
     */
    @Test
    fun `renders the sample week's rows from 150 to 276 dp wide, dark and light, to PNG files`() {
        val dates = WeekDays.MONDAY_TO_SUNDAY.dates(today)
        val week = WeekLayout.build(dates, SampleData.entries, today)
        Probe.renderDir.mkdirs()
        for (widthDp in listOf(150, 200, 245, 250, 276)) {
            val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
            val narrowPx = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
            val renderer = if (DayRowRenderer.stacksAt(widthDp)) stacked else rows
            for ((mode, palette) in modes) {
                val file = File(Probe.renderDir, "woche-${widthDp}dp-monday-$mode.png")
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
                val drawn = week.map { renderer.render(it, narrowPx, scale, palette) }
                write(header, drawn, palette, file, narrowPx)
                assertTrue(file.isFile)
            }
        }
    }

    @Test
    fun `stacked, a timed entry takes its time's line above its title's, a chip and an empty day one line each`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val timed = Probe.timed(thursday, icon = null)
        val pad = 2 * TileWeek.ROW_PAD
        // The weekday above the date: two lines, taller than a chip's or "frei"'s one line.
        val head = 2 * TileWeek.TODAY_LINE
        assertEquals(pad + head, stacked.heightDp(day(thursday)), 0.01f)
        assertEquals(pad + head, stacked.heightDp(day(thursday, chip(thursday))), 0.01f)
        assertEquals(pad + DayRowRenderer.TIME_LINE + TileWeek.LINE, stacked.heightDp(day(thursday, timed)), 0.01f)
        assertEquals(
            pad + TileWeek.LINE + TileWeek.LINE_GAP + DayRowRenderer.TIME_LINE + TileWeek.LINE,
            stacked.heightDp(day(thursday, chip(thursday), timed)),
            0.01f,
        )
        assertEquals(pad + TileWeek.LINE, rows.heightDp(day(thursday, timed)), 0.01f)
    }

    @Test
    fun `stacked, an entry's tap area spans its lines, a timed one its time's and its title's`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val day = day(thursday, chip(thursday), Probe.timed(thursday, icon = null))
        assertEquals(listOf(null, "10:00–11:00"), day.entries.map { it.time })
        val (chip, timed) = stacked.entryAreas(day, rowPx, scale)
        val left = scale.px(PhoneMonth.CARD_SIDE + DayRowRenderer.STACKED_COLUMN + TileWeek.COLUMN_GAP)
        assertEquals(left, chip.left, 0.01f)
        assertEquals(left, timed.left, 0.01f)
        assertEquals(scale.hairline + scale.px(TileWeek.ROW_PAD), chip.top, 0.01f)
        assertEquals(scale.px(TileWeek.LINE + TileWeek.LINE_GAP), chip.height(), 0.01f)
        assertEquals(chip.bottom, timed.top, 0.01f)
        assertEquals(scale.px(DayRowRenderer.TIME_LINE + TileWeek.LINE + TileWeek.LINE_GAP), timed.height(), 0.01f)
    }

    @Test
    fun `stacked, a timed entry's bar spans its time's line and its title's, and the date stands below the weekday`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val row = stacked.render(day(thursday, Probe.timed(thursday, icon = null)), rowPx, scale, SampleData.light)
        val barX = scale.px(PhoneMonth.CARD_SIDE + DayRowRenderer.STACKED_COLUMN + TileWeek.COLUMN_GAP + 1f)
        for (dp in listOf(DayRowRenderer.TIME_LINE / 2, DayRowRenderer.TIME_LINE + TileWeek.LINE / 2)) {
            val y = scale.hairline + scale.px(TileWeek.ROW_PAD + dp)
            assertTrue("no bar at $dp dp", Probe.near(row.getPixel(barX.toInt(), y.toInt()), Probe.RED))
        }
        val dateTop = scale.hairline + scale.px(TileWeek.ROW_PAD + TileWeek.TODAY_LINE)
        val columnEnd = scale.px(PhoneMonth.CARD_SIDE + DayRowRenderer.STACKED_COLUMN)
        assertTrue(row.has(SampleData.light.text, 0f, dateTop, columnEnd, dateTop + scale.px(TileWeek.TODAY_LINE)))
    }

    @Test
    fun `the stacked day column holds each weekday's name and each date`() {
        val weekday =
            fonts.paint(scale, TileWeek.WEEKDAY_FONT, FontWeight.BOLD, Color.BLACK).apply {
                letterSpacing = TileWeek.WEEKDAY_SPACING
            }
        val date =
            fonts.paint(scale, TileWeek.DATE_FONT, FontWeight.SEMI_BOLD, Color.BLACK).apply {
                fontFeatureSettings = "tnum"
            }
        val column = scale.px(DayRowRenderer.STACKED_COLUMN)
        for (name in texts.weekdays) {
            val width = weekday.measureText(name.uppercase(Locale.GERMAN))
            assertTrue("$name is ${width / scale.px(1f)} dp wide", width <= column)
        }
        for (number in 1..31) assertTrue("$number.", date.measureText("$number.") <= column)
    }

    @Test
    fun `stacked, today's head is its weekday in the accent, in one line it is Heute`() {
        // "Heute" reaches past the stacked column into the gap before the entries; the weekday "MI" does not.
        val gapLeft = scale.px(PhoneMonth.CARD_SIDE + DayRowRenderer.STACKED_COLUMN)
        val gapRight = gapLeft + scale.px(TileWeek.COLUMN_GAP)
        val headTop = scale.hairline + scale.px(TileWeek.ROW_PAD)
        val headBottom = headTop + scale.px(TileWeek.TODAY_LINE)
        for (palette in listOf(SampleData.light, SampleData.dark)) {
            val stackedRow = stacked.render(day(today), rowPx, scale, palette)
            val oneLine = rows.render(day(today), rowPx, scale, palette)
            assertTrue(stackedRow.has(palette.accent, 0f, headTop, gapLeft, headBottom))
            assertFalse(stackedRow.has(palette.accent, gapLeft, headTop, gapRight, headBottom))
            assertTrue(oneLine.has(palette.accent, gapLeft, headTop, gapRight, headBottom))
        }
    }

    @Test
    fun `the tap areas, the drawn lines and the height share one geometry, stacked or not`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val week =
            WeekLayout.build(
                listOf(thursday.minusDays(1), thursday),
                listOf(chip(thursday.minusDays(1), 2), chip(thursday), Probe.timed(thursday, icon = null)),
                today,
            )
        val day = week[1]
        assertEquals(listOf("Ganztags"), day.running)
        for ((renderer, column) in listOf(rows to TileWeek.DAY_COLUMN, stacked to DayRowRenderer.STACKED_COLUMN)) {
            val drawn = renderer.render(day, rowPx, scale, SampleData.light)
            val areas = renderer.entryAreas(day, rowPx, scale)
            assertEquals(2, areas.size)
            // Below the running titles' line.
            assertEquals(
                scale.hairline + scale.px(TileWeek.ROW_PAD + TileWeek.LINE + TileWeek.LINE_GAP),
                areas[0].top,
                0.01f,
            )
            assertEquals(areas[0].bottom, areas[1].top, 0.01f)
            // The last area ends one gap below the last line, inside the row's bottom padding.
            assertEquals(drawn.height - scale.px(TileWeek.ROW_PAD - TileWeek.LINE_GAP), areas[1].bottom, 1f)
            val barX = scale.px(PhoneMonth.CARD_SIDE + column + TileWeek.COLUMN_GAP + 1f)
            for (area in areas) {
                for (y in listOf(area.top + scale.px(1f), area.bottom - scale.px(TileWeek.LINE_GAP + 1f))) {
                    assertTrue("no bar at $y", Probe.near(drawn.getPixel(barX.toInt(), y.toInt()), Probe.RED))
                }
            }
        }
    }

    @Test
    fun `a day that leaves out entries draws a line counting them instead, and has no tap area there`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val full = day(thursday, chip(thursday), Probe.timed(thursday, icon = null))
        val capped = full.copy(more = 1)
        val text = SampleData.light.tertiaryText
        for (renderer in listOf(rows, stacked)) {
            val area = renderer.entryAreas(capped, rowPx, scale).single()
            val drawn = renderer.render(capped, rowPx, scale, SampleData.light)
            val right = rowPx.toFloat()
            assertTrue(drawn.has(text, area.left, area.bottom, right, area.bottom + scale.px(TileWeek.LINE)))
            val uncapped = renderer.render(full, rowPx, scale, SampleData.light)
            assertFalse(uncapped.has(text, area.left, 0f, right, uncapped.height.toFloat()))
        }
        // In one line per entry, the counting line is as tall as the entry it stands for.
        assertEquals(rows.heightDp(full), rows.heightDp(capped), 0.01f)
        assertEquals(
            stacked.heightDp(full) - DayRowRenderer.TIME_LINE,
            stacked.heightDp(capped),
            0.01f,
        )
    }

    @Test
    fun `the counting line names how many entries the day leaves out, one or more`() {
        assertEquals("+1 weiterer", texts.more(1))
        assertEquals("+2 weitere", texts.more(2))
        assertEquals("+3 weitere", texts.more(3))
        // The same two entries shown, one or two left out: the lines differ only in their count.
        val thursday = LocalDate.of(2026, 11, 12)
        val timed = { hour: Int ->
            Probe.timed(thursday, icon = null).copy(start = thursday.atTime(hour, 0), end = thursday.atTime(hour, 30))
        }
        val one = day(thursday, timed(8), timed(9), timed(10)).copy(more = 1)
        val two = day(thursday, timed(8), timed(9), timed(10), timed(11)).copy(more = 2)
        assertEquals(one.shown, two.shown)
        val drawn = { day: WeekDay -> rows.render(day, rowPx, scale, SampleData.light) }
        assertFalse(drawn(one).sameAs(drawn(two)))
        assertTrue(drawn(two).sameAs(drawn(day(thursday, timed(8), timed(9), timed(12), timed(13)).copy(more = 2))))
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
