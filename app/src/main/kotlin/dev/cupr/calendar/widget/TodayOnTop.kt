package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.RemoteViews
import dev.cupr.calendar.R
import kotlin.math.roundToInt

/**
 * Where a widget's list scrolls so today's row stands on top. The only list
 * scroll a widget's views offer, `setScrollPosition`, is a smooth scroll to a
 * position, which ends with a row below the visible ones as the last visible
 * row; so the list scrolls not to today's row but to the last row that still
 * fits below it, and today's row stands on top when the list comes from above.
 * The list's height is the widget's height in portrait less its card and
 * header ([DrawnWidget.listPx]).
 */
internal object TodayOnTop {
    /** The row to scroll to, and the summed height in px of today's row through it. */
    data class Target(
        val index: Int,
        val rowsPx: Int,
    )

    /**
     * The last row at or after today whose rows from today's on fit into the
     * list, listPx high, by the rows' heights in px: the last row when the
     * rows from today's on fit into listPx; otherwise the last that fits into
     * listPx less extraPx, today's row when it alone does not. extraPx is
     * the room the smooth scroll leaves below a target that is not the
     * list's last row: AOSP's `AbsListView.PositionScroller` scrolls down
     * until the target's bottom is its `mExtraScroll` above the list's
     * bottom, which is `ViewConfiguration.getScaledFadingEdgeLength()`,
     * 12 dp (the list's bottom padding, 0 here, when that is more); for the
     * last row only that padding. Null when today is no row's index, as -1
     * from indexOfFirst.
     */
    fun target(
        heightsPx: List<Int>,
        today: Int,
        listPx: Float,
        extraPx: Int,
    ): Target? {
        if (today !in heightsPx.indices) return null
        val throughLast = heightsPx.subList(today, heightsPx.size).sum()
        return if (throughLast <= listPx) {
            Target(heightsPx.lastIndex, throughLast)
        } else {
            fitting(heightsPx, today, listPx - extraPx)
        }
    }

    /**
     * The last row from today's on whose rows fit into roomPx, today's row
     * when it alone does not, for rows that through the last pass roomPx.
     */
    private fun fitting(
        heightsPx: List<Int>,
        today: Int,
        roomPx: Float,
    ): Target {
        var index = today
        var rowsPx = heightsPx[today]
        while (rowsPx + heightsPx[index + 1] <= roomPx) {
            index += 1
            rowsPx += heightsPx[index]
        }
        return Target(index, rowsPx)
    }

    /**
     * The widget's height in portrait, its greatest height (see
     * [DrawnWidget.widthDp]), and the option it came from, with the least
     * height beside it for the log. Without a reported height, 4 cells, and
     * the log says so.
     */
    fun portraitHeight(
        options: Bundle,
        tag: String,
        id: Int,
    ): WidgetHeight {
        val key = AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        val reported = options.getInt(key) > 0
        val dp = DrawnWidget.option(options, key, DrawnWidget.DEFAULT_HEIGHT_DP, tag, id)
        val least = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return WidgetHeight(dp, if (reported) "$key, $least dp least" else "no $key, the default")
    }

    /**
     * Scrolls the list of views to its [target], with the smooth scroll's
     * room below it from context's fading edge length: rows are the drawn
     * rows' heights in px, today the index of today's row, or -1 when none is
     * today, which leaves the list unscrolled; listPx is the list's height in
     * a widget of height ([DrawnWidget.listPx]). Returns where it scrolled and
     * from what, for the drawing's log line ([DrawnWidget.logDrawn]).
     */
    fun scroll(
        context: Context,
        views: RemoteViews,
        rows: List<Int>,
        today: Int,
        listPx: Float,
        height: WidgetHeight,
    ): String {
        val extraPx = ViewConfiguration.get(context).scaledFadingEdgeLength
        val target = target(rows, today, listPx, extraPx)
        target?.let { views.setScrollPosition(R.id.list, it.index) }
        return "; today is row $today, scrolled to ${target?.index ?: "none"}, rows from today to it " +
            "${target?.rowsPx ?: 0} px of a list ${listPx.roundToInt()} px high, less $extraPx px above a target " +
            "not last, in ${height.dp} dp (${height.from})"
    }
}
