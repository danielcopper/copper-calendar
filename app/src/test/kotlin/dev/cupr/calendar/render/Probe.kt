package dev.cupr.calendar.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.month.Bar
import dev.cupr.calendar.month.DAYS_IN_WEEK
import dev.cupr.calendar.month.Day
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.PhoneMonth.CARD_INSET
import dev.cupr.calendar.render.PhoneMonth.CELL_SIDE
import dev.cupr.calendar.render.PhoneMonth.CELL_TOP
import dev.cupr.calendar.render.PhoneMonth.GAP
import dev.cupr.calendar.render.PhoneMonth.LANE
import dev.cupr.calendar.render.PhoneMonth.LINE
import dev.cupr.calendar.render.PhoneMonth.MARK
import dev.cupr.calendar.render.PhoneMonth.NUMBER_ROW
import dev.cupr.calendar.render.PhoneMonth.TIMED_SIDE
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.widget.WidgetHost
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Where to look in a drawn week row, from the same measures the renderer
 * uses ([PhoneMonth]), at a Pixel 6's density (2.625, 420 dpi) and a
 * 4-cell-wide widget; and where the render tests write their PNGs.
 */
internal object Probe {
    const val DENSITY = 2.625f
    val scale = Scale(DENSITY)

    /** A 4-cell-wide widget's width. */
    const val WIDTH_DP = WidgetHost.WIDTH_DP
    val widthPx = scale.px(WIDTH_DP - 2 * CARD_INSET).roundToInt()

    /** Where the render tests write their PNGs (`mise run render`). */
    val renderDir = File(System.getProperty("render.dir") ?: "build/render")

    /** A bitmap as PNG bytes. */
    fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream()
            .also { assertTrue("the PNG was not written", bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
            .toByteArray()

    private const val PNG_QUALITY = 100

    /** The entries' own colour in these tests. */
    const val RED = 0xFFFF0000.toInt()

    /** A week row renderer with the app's fonts, icons and drawings. */
    fun rows(): WeekRowRenderer {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        return WeekRowRenderer(Fonts(assets), Icons(assets), Drawings(assets))
    }

    /** An all-day entry of one day, red. */
    fun allDay(
        date: LocalDate,
        drawing: String? = null,
        icon: String? = null,
    ) = Entry(
        start = date.atStartOfDay(),
        end = date.plusDays(1).atStartOfDay(),
        allDay = true,
        title = "Ganztags",
        calendar = SampleData.FAMILY,
        color = RED,
        icon = icon,
        drawing = drawing,
    )

    /** A timed entry from 10 to 11, red. */
    fun timed(
        date: LocalDate,
        icon: String?,
        drawing: String? = null,
    ) = Entry(
        start = date.atTime(10, 0),
        end = date.atTime(11, 0),
        allDay = false,
        title = "Termin",
        calendar = SampleData.FAMILY,
        color = RED,
        icon = icon,
        drawing = drawing,
    )

    /**
     * The lantern drawing's colour at (21.5, 25) of its 64-unit box: its
     * body, #f08a36, under its glow, #ffd56b at 0.35: rgb(245, 164, 73).
     */
    const val LANTERN_BODY = 0xFFF5A449.toInt()

    /** x or y of a point of the lantern drawing in a box of size px at start. */
    fun lanternPoint(
        start: Float,
        size: Float,
        unit: Float,
    ): Float = start + size * unit / LANTERN_BOX

    private const val LANTERN_BOX = 64f

    /** Whether a pixel is within 3 of a colour in each channel. */
    fun near(
        pixel: Int,
        color: Int,
    ): Boolean =
        Color.alpha(pixel) == 255 &&
            listOf<(Int) -> Int>({ Color.red(it) }, { Color.green(it) }, { Color.blue(it) }).all { channel ->
                kotlin.math.abs(channel(pixel) - channel(color)) <= 3
            }

    private val column = widthPx / DAYS_IN_WEEK.toFloat()

    /** The left edge of a column's content. */
    fun cellX(index: Int): Float = index * column + (if (index > 0) scale.hairline else 0f) + scale.px(CELL_SIDE)

    /** The left edge of a column. */
    fun columnX(index: Int): Float = index * column

    /** The top of a cell's first entry line below lanes of bars. */
    fun lineTop(
        lanes: Int,
        line: Int = 0,
    ): Float = scale.hairline + scale.px(CELL_TOP + NUMBER_ROW + GAP + lanes * LANE + GAP + line * (LINE + GAP))

    /** The vertical middle of an entry line. */
    fun lineMiddle(
        lanes: Int,
        line: Int = 0,
    ): Float = lineTop(lanes, line) + scale.px(LINE) / 2

    /** The vertical middle of a lane of bars. */
    fun laneMiddle(lane: Int): Float =
        scale.hairline + scale.px(CELL_TOP + NUMBER_ROW + lane * LANE + PhoneMonth.BAR_HEIGHT / 2)

    /** x of a point of a timed entry's mark, in units of MDI's 24-unit box. */
    fun markX(
        index: Int,
        unit: Float,
    ): Float = cellX(index) + scale.px(TIMED_SIDE) + scale.px(MARK) * unit / Icons.BOX

    fun day(
        date: LocalDate,
        shown: List<Entry> = emptyList(),
        otherMonth: Boolean = false,
        past: Boolean = false,
        today: Boolean = false,
        holiday: Boolean = false,
        marks: List<Entry> = emptyList(),
        more: Int = 0,
    ) = Day(date, otherMonth, past, today, holiday, marks, shown, more)

    /** A week from monday, its days as given by index, the others empty. */
    fun week(
        monday: LocalDate,
        days: Map<Int, Day> = emptyMap(),
        bars: List<Bar> = emptyList(),
    ) = Week(List(DAYS_IN_WEEK) { days[it] ?: day(monday.plusDays(it.toLong())) }, bars)

    fun Bitmap.at(
        x: Float,
        y: Float,
    ): Int = getPixel(x.toInt(), y.toInt())

    /** Whether any pixel in the box has this exact colour. */
    fun Bitmap.has(
        color: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean =
        (left.toInt() until right.toInt().coerceAtMost(width)).any { x ->
            (top.toInt() until bottom.toInt().coerceAtMost(height)).any { y -> getPixel(x, y) == color }
        }
}
