package dev.cupr.calendar.render

import android.graphics.Bitmap
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.month.Bar
import dev.cupr.calendar.month.DAYS_IN_WEEK
import dev.cupr.calendar.month.Day
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.PhoneMonth.CELL_SIDE
import dev.cupr.calendar.render.PhoneMonth.CELL_TOP
import dev.cupr.calendar.render.PhoneMonth.GAP
import dev.cupr.calendar.render.PhoneMonth.LANE
import dev.cupr.calendar.render.PhoneMonth.LINE
import dev.cupr.calendar.render.PhoneMonth.MARK
import dev.cupr.calendar.render.PhoneMonth.NUMBER_ROW
import dev.cupr.calendar.render.PhoneMonth.TIMED_SIDE
import dev.cupr.calendar.widget.MonthWidget
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Where to look in a drawn week row, from the same measures the renderer
 * uses ([PhoneMonth]), at a Pixel 6's density (2.625, 420 dpi) and a
 * 4-cell-wide widget.
 */
internal object Probe {
    const val DENSITY = 2.625f
    val scale = Scale(DENSITY)
    val widthPx = scale.px(MonthWidget.contentWidthDp(276)).roundToInt()
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
        drawings: List<String> = emptyList(),
        more: Int = 0,
    ) = Day(date, otherMonth, past, today, holiday, drawings, shown, more)

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
