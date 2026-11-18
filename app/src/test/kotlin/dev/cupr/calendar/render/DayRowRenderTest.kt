package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.CalendarVariables.FAMILY
import dev.cupr.calendar.model.DashboardColor.CalendarVariable
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Kinds
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.render.Probe.at
import dev.cupr.calendar.render.Probe.has
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.week.End
import dev.cupr.calendar.week.RowTime
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
    private val now = SampleData.now

    /** An entry's row's padding, above and below its lines. */
    private val pads = 2 * TileWeek.ENTRY_PAD
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
            val week = WeekLayout.build(dates, SampleData.entries, now)
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
     * From the least width, 110 dp, to 4 cells (276 dp), stacked below 250 dp
     * as the widget draws them, on both sides of that switch.
     */
    @Test
    fun `renders the sample week's rows from 110 to 276 dp wide, dark and light, to PNG files`() {
        val dates = WeekDays.MONDAY_TO_SUNDAY.dates(today)
        val week = WeekLayout.build(dates, SampleData.entries, now)
        Probe.renderDir.mkdirs()
        for (widthDp in listOf(110, 150, 200, 245, 250, 276)) {
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

    /**
     * The sample week with entries over several days and one of today that
     * is over, 4 cells wide and stacked at 200 dp: rows on each day an entry
     * covers, and the end of those that run on after Sunday.
     */
    @Test
    fun `renders the sample week with entries over several days, dark and light, to PNG files`() {
        val dates = WeekDays.MONDAY_TO_SUNDAY.dates(today)
        val at = { day: Int, hour: Int -> LocalDate.of(2026, 11, day).atTime(hour, 0) }
        val timed = { start: java.time.LocalDateTime, end: java.time.LocalDateTime, title: String ->
            Probe.timed(today, icon = null).copy(start = start, end = end, title = title, uid = title)
        }
        val entries =
            SampleData.entries +
                listOf(
                    timed(at(8, 20), at(9, 6), "Nachtfahrt"),
                    timed(at(10, 9), at(12, 16), "Messe"),
                    timed(at(11, 7), at(11, 8), "Frühstück"),
                    timed(at(14, 18), at(16, 10), "Reise"),
                    Probe.allDay(LocalDate.of(2026, 11, 13)).copy(
                        end = LocalDate.of(2026, 11, 18).atStartOfDay(),
                        title = "Urlaub",
                        icon = "mdi:bag-suitcase",
                    ),
                )
        val week = WeekLayout.build(dates, entries, now)
        Probe.renderDir.mkdirs()
        for ((widthDp, renderer) in listOf(Probe.WIDTH_DP to rows, 200 to stacked)) {
            val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
            val width = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
            for ((mode, palette) in modes) {
                val file = File(Probe.renderDir, "woche-multi-day-${widthDp}dp-$mode.png")
                file.delete()
                val header =
                    chrome.weekHeader(
                        context.getString(WeekDays.MONDAY_TO_SUNDAY.title),
                        WeekLayout.range(dates.first(), dates.last()),
                        null,
                        headerPx,
                        scale,
                        palette,
                    )
                write(header, week.map { renderer.render(it, width, scale, palette) }, palette, file, width)
                assertTrue(file.isFile)
            }
        }
    }

    @Test
    fun `stacked, a timed entry takes its time's line above its title's, an all-day one and a free day one line`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val timed = Probe.timed(thursday, icon = null)
        val pad = 2 * TileWeek.ROW_PAD
        // The weekday above the date: two lines, taller than an all-day row's or "frei"'s one line.
        val head = 2 * TileWeek.TODAY_LINE
        assertEquals(pad + head, stacked.heightDp(day(thursday)), 0.01f)
        assertEquals(pad + head, stacked.heightDp(day(thursday, allDayEntry(thursday))), 0.01f)
        val twoLines = pads + DayRowRenderer.TIME_LINE + TileWeek.LINE
        assertEquals(pad + twoLines, stacked.heightDp(day(thursday, timed)), 0.01f)
        assertEquals(
            pad + pads + TileWeek.LINE + TileWeek.LINE_GAP + twoLines,
            stacked.heightDp(day(thursday, allDayEntry(thursday), timed)),
            0.01f,
        )
        assertEquals(pad + pads + TileWeek.LINE, rows.heightDp(day(thursday, timed)), 0.01f)
    }

    @Test
    fun `stacked, an entry's tap area spans its lines, a timed one its time's and its title's`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val day = day(thursday, allDayEntry(thursday), Probe.timed(thursday, icon = null))
        assertEquals(listOf(null, RowTime.Within("10:00", "11:00")), day.entries.map { it.time })
        val (allDay, timed) = stacked.entryAreas(day, rowPx, scale)
        val left = scale.px(PhoneMonth.CARD_SIDE + DayRowRenderer.STACKED_COLUMN + TileWeek.COLUMN_GAP)
        assertEquals(left, allDay.left, 0.01f)
        assertEquals(left, timed.left, 0.01f)
        assertEquals(scale.hairline + scale.px(TileWeek.ROW_PAD), allDay.top, 0.01f)
        assertEquals(scale.px(pads + TileWeek.LINE + TileWeek.LINE_GAP), allDay.height(), 0.01f)
        assertEquals(allDay.bottom, timed.top, 0.01f)
        assertEquals(
            scale.px(pads + DayRowRenderer.TIME_LINE + TileWeek.LINE + TileWeek.LINE_GAP),
            timed.height(),
            0.01f,
        )
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
    fun `a timed entry's bar takes the dashboard's colour in the mode, not its own`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val family = Probe.timed(thursday, icon = null).copy(dashboardColor = CalendarVariable(FAMILY))
        val barX = scale.px(PhoneMonth.CARD_SIDE + DayRowRenderer.STACKED_COLUMN + TileWeek.COLUMN_GAP + 1f)
        val y = scale.hairline + scale.px(TileWeek.ROW_PAD + DayRowRenderer.TIME_LINE / 2)

        for (palette in listOf(SampleData.light, SampleData.dark)) {
            val row = stacked.render(day(thursday, family), rowPx, scale, palette)
            val bar = row.getPixel(barX.toInt(), y.toInt())
            assertTrue(Integer.toHexString(bar), Probe.near(bar, palette.calendars.getValue(FAMILY)))
        }
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
                listOf(
                    allDayEntry(thursday.minusDays(1), 2),
                    allDayEntry(thursday),
                    Probe.timed(thursday, icon = null),
                ),
                now,
            )
        val day = week[1]
        assertEquals(listOf(true, false, false), day.entries.map { it.continued })
        for ((renderer, column) in listOf(rows to TileWeek.DAY_COLUMN, stacked to DayRowRenderer.STACKED_COLUMN)) {
            val drawn = renderer.render(day, rowPx, scale, SampleData.light)
            val areas = renderer.entryAreas(day, rowPx, scale)
            assertEquals(3, areas.size)
            assertEquals(scale.hairline + scale.px(TileWeek.ROW_PAD), areas[0].top, 0.01f)
            assertEquals(areas[0].bottom, areas[1].top, 0.01f)
            assertEquals(areas[1].bottom, areas[2].top, 0.01f)
            // The last area ends one gap below the last row, inside the day's bottom padding.
            assertEquals(drawn.height - scale.px(TileWeek.ROW_PAD - TileWeek.LINE_GAP), areas[2].bottom, 1f)
            val barX = scale.px(PhoneMonth.CARD_SIDE + column + TileWeek.COLUMN_GAP + TileWeek.BAR / 2)
            for (area in areas) {
                // Inside the bar's ends, which the row's rounded corners cut.
                for (y in listOf(area.top + scale.px(4f), area.bottom - scale.px(TileWeek.LINE_GAP + 4f))) {
                    assertTrue("no bar at $y", Probe.near(drawn.getPixel(barX.toInt(), y.toInt()), Probe.RED))
                }
            }
        }
    }

    @Test
    fun `a day that leaves out entries draws a line counting them instead, and has no tap area there`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val full = day(thursday, allDayEntry(thursday), Probe.timed(thursday, icon = null))
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
        // The counting line is one line, without the padding of the entry's row it stands for.
        assertEquals(rows.heightDp(full) - pads, rows.heightDp(capped), 0.01f)
        assertEquals(
            stacked.heightDp(full) - pads - DayRowRenderer.TIME_LINE,
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
    ): WeekDay = WeekLayout.build(listOf(date), entries.toList(), now).single()

    private fun allDayEntry(
        first: LocalDate,
        days: Long = 1,
        kind: String? = null,
    ) = Probe.allDay(first).copy(end = first.plusDays(days).atStartOfDay(), kind = kind)

    @Test
    fun `an entry's tap area lies over its row, one row lower below a row continued from an earlier day`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val plain = rows.entryAreas(day(thursday, allDayEntry(thursday)), rowPx, scale).single()
        assertEquals(scale.hairline + scale.px(TileWeek.ROW_PAD), plain.top, 0.01f)
        assertEquals(scale.px(PhoneMonth.CARD_SIDE + TileWeek.DAY_COLUMN + TileWeek.COLUMN_GAP), plain.left, 0.01f)
        assertEquals(rowPx - scale.px(PhoneMonth.CARD_SIDE), plain.right, 0.01f)
        val week =
            WeekLayout.build(
                listOf(thursday.minusDays(1), thursday),
                listOf(allDayEntry(thursday.minusDays(1), 2), allDayEntry(thursday).copy(title = "Eigener")),
                now,
            )
        assertEquals(listOf("Ganztags", "Eigener"), week[1].entries.map { it.entry.title })
        val (continued, own) = rows.entryAreas(week[1], rowPx, scale)
        assertEquals(plain, continued)
        assertEquals(plain.top + scale.px(pads + TileWeek.LINE + TileWeek.LINE_GAP), own.top, 0.01f)
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
        val past = rows.render(day(monday, allDayEntry(monday)), rowPx, scale, SampleData.dark)
        val barX = scale.px(PhoneMonth.CARD_SIDE + TileWeek.DAY_COLUMN + TileWeek.COLUMN_GAP + 1f)
        val barY = scale.hairline + scale.px(TileWeek.ROW_PAD + 10f)
        val bar = past.getPixel(barX.toInt(), barY.toInt())
        assertEquals(128f, Color.alpha(bar).toFloat(), 2f)
    }

    @Test
    fun `a public holiday's date is red`() {
        val wednesday = LocalDate.of(2026, 11, 18)
        val holiday =
            rows.render(
                day(wednesday, allDayEntry(wednesday, kind = Kinds.HOLIDAY)),
                rowPx,
                scale,
                SampleData.light,
            )
        val plain = rows.render(day(wednesday, allDayEntry(wednesday)), rowPx, scale, SampleData.light)
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

    @Test
    fun `a timed row is tinted at 6 percent, an all-day row at 12, from the entries' column to the row's edge`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val day = day(thursday, allDayEntry(thursday), Probe.timed(thursday, icon = null))
        for (renderer in listOf(rows, stacked)) {
            val drawn = renderer.render(day, rowPx, scale, SampleData.light)
            val (allDay, timed) = renderer.entryAreas(day, rowPx, scale)
            for ((area, tint) in listOf(allDay to TileWeek.ALL_DAY_TINT, timed to TileWeek.TIMED_TINT)) {
                val y = area.top + scale.px(TileWeek.ENTRY_PAD + TileWeek.LINE / 2)
                // In its padding at the right end, and between its bar and its mark.
                for (x in listOf(
                    area.right - scale.px(TileWeek.ENTRY_END / 2),
                    area.left + scale.px(TileWeek.BAR + 2f),
                )) {
                    val pixel = drawn.at(x, y)
                    assertEquals("alpha at $x", tint * 255, Color.alpha(pixel).toFloat(), 1f)
                    assertTrue(Integer.toHexString(pixel), Color.red(pixel) > 200 && Color.green(pixel) < 60)
                }
                assertEquals("beyond the row's edge", 0, Color.alpha(drawn.at(area.right + scale.px(1f), y)))
            }
        }
    }

    @Test
    fun `a timed row is as tall as an all-day row, its time quiet at its right end, no column at its left`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val day = day(thursday, allDayEntry(thursday), Probe.timed(thursday, icon = null))
        val drawn = rows.render(day, rowPx, scale, SampleData.light)
        val (allDay, timed) = rows.entryAreas(day, rowPx, scale)
        assertEquals(allDay.height(), timed.height(), 0.01f)
        val time = SampleData.light.secondaryText
        val end = timed.right - scale.px(TileWeek.ENTRY_END)
        assertTrue(drawn.has(time, end - scale.px(20f), timed.top, end, timed.bottom))
        assertFalse(drawn.has(time, timed.left, timed.top, timed.left + scale.px(60f), timed.bottom))
    }

    @Test
    fun `a long title on a timed row is cut before its time, and the time still draws`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val long = Probe.timed(thursday, icon = null).copy(title = "Ein sehr langer Titel, der nie in die Zeile passt")
        val day = day(thursday, long)
        val drawn = rows.render(day, rowPx, scale, SampleData.light)
        val area = rows.entryAreas(day, rowPx, scale).single()
        val timePaint =
            fonts.paint(scale, TileWeek.TIME_FONT, FontWeight.REGULAR, Color.BLACK).apply {
                fontFeatureSettings = "tnum"
            }
        val end = area.right - scale.px(TileWeek.ENTRY_END)
        val timeX = end - timePaint.measureText("10:00–11:00")
        val titleEnd = timeX - scale.px(TileWeek.TIME_GAP + TileWeek.PART_GAP)
        val text = SampleData.light.text
        assertTrue(
            "the title reaches its end",
            drawn.has(text, titleEnd - scale.px(12f), area.top, titleEnd, area.bottom),
        )
        assertFalse(drawn.has(text, timeX - scale.px(TileWeek.TIME_GAP), area.top, area.right, area.bottom))
        assertTrue(drawn.has(SampleData.light.secondaryText, timeX, area.top, end, area.bottom))
    }

    @Test
    fun `an all-day row without a mark leaves its room free, so its title starts where a timed row's does`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val day = day(thursday, allDayEntry(thursday).copy(title = "Termin"), Probe.timed(thursday, icon = null))
        val drawn = rows.render(day, rowPx, scale, SampleData.light)
        val text = SampleData.light.text
        val starts =
            rows.entryAreas(day, rowPx, scale).map { area ->
                (area.left.toInt() until area.right.toInt()).first { x ->
                    drawn.has(text, x.toFloat(), area.top, x + 1f, area.bottom)
                }
            }
        assertEquals(starts[0].toFloat(), starts[1].toFloat(), 1f)
    }

    @Test
    fun `stacked, a timed row is one tinted field over its time's line and its title's, its bar spanning both`() {
        val thursday = LocalDate.of(2026, 11, 12)
        val day = day(thursday, Probe.timed(thursday, icon = null))
        val drawn = stacked.render(day, rowPx, scale, SampleData.light)
        val area = stacked.entryAreas(day, rowPx, scale).single()
        val x = area.right - scale.px(TileWeek.ENTRY_END / 2)
        val barX = area.left + scale.px(1f)
        val pad = TileWeek.ENTRY_PAD
        for (dp in listOf(
            pad + 3f,
            pad + DayRowRenderer.TIME_LINE,
            pad + DayRowRenderer.TIME_LINE + TileWeek.LINE - 3f,
        )) {
            val y = area.top + scale.px(dp)
            assertEquals("tint at $dp dp", TileWeek.TIMED_TINT * 255, Color.alpha(drawn.at(x, y)).toFloat(), 1f)
            assertTrue("no bar at $dp dp", Probe.near(drawn.at(barX, y), Probe.RED))
        }
    }

    @Test
    fun `a timed row of today whose end has passed is drawn at half opacity, one still to end in full`() {
        val over = Probe.timed(today, icon = null).copy(start = today.atTime(7, 0), end = today.atTime(8, 0))
        val ahead = Probe.timed(today, icon = null)
        val day = day(today, over, ahead)
        assertEquals(listOf(true, false), day.entries.map { it.over })
        // Without today's tint under the bars, so their alpha is their own.
        val drawn = rows.render(day.copy(today = false), rowPx, scale, SampleData.light)
        val alphas =
            rows.entryAreas(day, rowPx, scale).map { area ->
                Color.alpha(
                    drawn.at(
                        area.left + scale.px(1f),
                        area.top + scale.px(TileWeek.ENTRY_PAD + TileWeek.LINE / 2),
                    ),
                )
            }
        assertEquals(128f, alphas[0].toFloat(), 2f)
        assertEquals(255, alphas[1])
    }

    @Test
    fun `a row's time and description are worded from the app's texts`() {
        val tuesday = LocalDate.of(2026, 11, 17)
        assertEquals("12:30–13:30", texts.time(RowTime.Within("12:30", "13:30")))
        assertEquals("18:00", texts.time(RowTime.Within("18:00", null)))
        assertEquals("ab 09:00", texts.time(RowTime.From("09:00")))
        assertEquals("Tag 2 von 3", texts.time(RowTime.DayOf(2, 3)))
        assertEquals("bis 24:00", texts.time(RowTime.Until(End.At("24:00"))))
        assertEquals("bis Di, 18:00", texts.time(RowTime.Until(End.Later(tuesday, true, "18:00"))))
        assertEquals("bis Di", texts.time(RowTime.Until(End.Later(tuesday, true, null))))
        assertEquals("bis 31.12.", texts.time(RowTime.Until(End.Later(LocalDate.of(2026, 12, 31), false, null))))
        assertEquals("bis 5.1., 08:00", texts.time(RowTime.Until(End.Later(LocalDate.of(2027, 1, 5), false, "08:00"))))
        assertEquals("08:00 – Di, 18:00", texts.time(RowTime.FromTo("08:00", End.Later(tuesday, true, "18:00"))))
        val sunday = LocalDate.of(2026, 11, 15)
        val trip = Probe.allDay(sunday).copy(end = tuesday.atStartOfDay())
        val timed = Probe.timed(sunday, icon = null)
        val day = WeekLayout.build(WeekDays.MONDAY_TO_SUNDAY.dates(sunday), listOf(trip, timed), now).last()
        assertEquals(listOf("Ganztags bis Mo", "Termin 10:00–11:00"), day.entries.map(rows::describe))
        assertEquals(listOf("Ganztags bis Mo", "10:00–11:00 Termin"), day.entries.map(stacked::describe))
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
