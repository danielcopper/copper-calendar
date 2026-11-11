package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.view.ViewConfiguration
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.render.Scale
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
        while (index < heightsPx.lastIndex && rowsPx + heightsPx[index + 1] <= roomPx) {
            index += 1
            rowsPx += heightsPx[index]
        }
        // Rounded down: a padding a fraction short would leave today's row that much below the top.
        val paddingPx = if (rowsPx <= roomPx) (listPx - rowsPx).toInt() else 0
        return Target(index, rowsPx, paddingPx)
    }

    /**
     * Scrolls the list of views to its [target] and sets the list's bottom
     * padding to the target's, with the smooth scroll's room below it from
     * context's fading edge length: heightsPx are the drawn rows' heights,
     * today the index of today's row; the list is the widget's height less
     * its card and a header headerPx high ([DrawnWidget.listPx]). Without a
     * height the launcher reports, it scrolls today's row only into view,
     * without padding (D27). When no row is today (-1), the list does not
     * scroll and the log warns. Returns where it scrolls and from what, for
     * the drawing's log line ([DrawnWidget.logDrawn]).
     */
    fun scroll(
        context: Context,
        views: RemoteViews,
        heightsPx: List<Int>,
        today: Int,
        height: WidgetHeight?,
        headerPx: Int,
        tag: String,
        id: Int,
    ): String {
        val extraPx = ViewConfiguration.get(context).scaledFadingEdgeLength
        val scale = Scale(context.resources.displayMetrics.density)
        val listPx = height?.let { DrawnWidget.listPx(scale, it.dp, headerPx) }
        val target =
            when {
                today !in heightsPx.indices -> null
                listPx == null -> Target(today, heightsPx[today], 0)
                else -> target(heightsPx, today, listPx, extraPx)
            }
        // setViewPadding sets all four sides; the list has no padding but this one, which an earlier update may
        // have set and which is cleared when there is none.
        views.setViewPadding(R.id.list, 0, 0, 0, target?.paddingPx ?: 0)
        if (target == null) {
            Log.w(tag, "Widget $id: no row is today, so its list does not scroll")
        } else {
            views.setScrollPosition(R.id.list, target.index)
        }
        return describe(target, today, heightsPx, listPx, extraPx, height)
    }

    /** Where the list scrolls and from what, as [scroll] returns it. */
    private fun describe(
        target: Target?,
        today: Int,
        heightsPx: List<Int>,
        listPx: Float?,
        extraPx: Int,
        height: WidgetHeight?,
    ): String =
        when {
            target == null -> {
                "; no row is today"
            }

            listPx == null || height == null -> {
                "; today is row $today, scrolls it into view: no ${AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT}"
            }

            else -> {
                reach(target, today, heightsPx, listPx, extraPx) + "; ${height.dp} dp (${height.from})"
            }
        }

    /** How far the scroll to target reaches in a list listPx high, and the padding it gets, for [describe]. */
    private fun reach(
        target: Target,
        today: Int,
        heightsPx: List<Int>,
        listPx: Float,
        extraPx: Int,
    ): String {
        val count = target.index - today + 1
        val rows = if (count == 1) "1 row" else "$count rows"
        val padding =
            when {
                target.paddingPx > 0 -> {
                    "bottom padding ${target.paddingPx} px, at least the $extraPx px the scroll stops above the bottom"
                }

                target.index == heightsPx.lastIndex && target.rowsPx <= listPx -> {
                    "no padding: the rows to the end fit"
                }

                else -> {
                    "no padding: today's row is taller than the list less the scroll's $extraPx px"
                }
            }
        return "; today is row $today, scrolls to row ${target.index}, $rows, ${target.rowsPx} of the list's " +
            "${listPx.roundToInt()} px, $padding"
    }
}
