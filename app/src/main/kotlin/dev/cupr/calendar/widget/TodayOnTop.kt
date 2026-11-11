package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.RemoteViews
import dev.cupr.calendar.R
import kotlin.math.roundToInt

/**
 * Where a widget's list scrolls so today's row stands flush on top. The only
 * list scroll a widget's views offer, `setScrollPosition`, is a smooth scroll
 * to a position, AOSP's `AbsListView.PositionScroller`, which ends with the
 * target's bottom at the list's bottom padding: for a target below the
 * visible rows (`MOVE_DOWN_POS`) at the padding or its
 * `mExtraScroll`, whichever is more, for one already shown (`scrollToVisible`)
 * at the padding. So the list scrolls not to today's row but to the last row
 * that still fits below it, and gets as much bottom padding as those rows
 * leave of its height; with `clipToPadding` false the rows after the target
 * still show in that padding, and today's row stands at the top when the list
 * comes from above. The list's height is the widget's height in portrait
 * less its card and header ([DrawnWidget.listPx]).
 */
internal object TodayOnTop {
    /**
     * The row to scroll to, the summed height in px of today's row through
     * it, and the list's bottom padding in px that ends the scroll with
     * today's row at the top.
     */
    data class Target(
        val index: Int,
        val rowsPx: Int,
        val paddingPx: Int,
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
     * 12 dp, or the list's bottom padding when that is more; for the last
     * row only that padding. The padding is what the rows leave of
     * listPx, so at least extraPx; none for the last row, which the scroll
     * brings to the list's bottom, and none for today's row alone when it is
     * taller than listPx less extraPx. Null when today is no row's index, as
     * -1 from indexOfFirst.
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
            Target(heightsPx.lastIndex, throughLast, 0)
        } else {
            fitting(heightsPx, today, listPx, listPx - extraPx)
        }
    }

    /**
     * The last row from today's on whose rows fit into roomPx, today's row
     * when it alone does not, for rows that through the last pass roomPx;
     * with the padding they leave of listPx when they fit.
     */
    private fun fitting(
        heightsPx: List<Int>,
        today: Int,
        listPx: Float,
        roomPx: Float,
    ): Target {
        var index = today
        var rowsPx = heightsPx[today]
        while (rowsPx + heightsPx[index + 1] <= roomPx) {
            index += 1
            rowsPx += heightsPx[index]
        }
        // Rounded down: a padding a fraction short would leave today's row that much below the top.
        val paddingPx = if (rowsPx <= roomPx) (listPx - rowsPx).toInt() else 0
        return Target(index, rowsPx, paddingPx)
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
     * Scrolls the list of views to its [target] and sets the list's bottom
     * padding to the target's, 0 without a target, with the smooth scroll's
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
        val paddingPx = target?.paddingPx ?: 0
        views.setViewPadding(R.id.list, 0, 0, 0, paddingPx)
        target?.let { views.setScrollPosition(R.id.list, it.index) }
        return "; today is row $today, scrolled to ${target?.index ?: "none"}, rows from today to it " +
            "${target?.rowsPx ?: 0} px of a list ${listPx.roundToInt()} px high, bottom padding $paddingPx px, " +
            "less $extraPx px above a target not last, in ${height.dp} dp (${height.from})"
    }
}
