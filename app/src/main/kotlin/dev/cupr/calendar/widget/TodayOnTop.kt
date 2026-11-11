package dev.cupr.calendar.widget

import android.content.Context
import android.util.Log
import android.view.ViewConfiguration
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.render.Scale
import kotlin.math.roundToInt

/**
 * Where a widget's list scrolls so today's row stands flush on top, as D27
 * describes it with the rule by which AOSP's `AbsListView.PositionScroller`
 * ends a smooth scroll to a row: not to today's row but to the last row that
 * still fits below it with the scroll's room to spare, with a bottom padding
 * of what those rows leave of the list's height ([DrawnWidget.listPx]).
 */
internal object TodayOnTop {
    /** Why a target's list gets the bottom padding it gets (D27). */
    enum class Fit {
        /** The rows from today's through the target fit with the scroll's room to spare; padded by what they leave. */
        PADDED,

        /** The rows from today's to the end fit into the list; the last row is the target, without padding. */
        TO_THE_END,

        /** Today's row alone is taller than the list less the scroll's room; it is the target, without padding. */
        TODAY_TOO_TALL,
    }

    /**
     * The row to scroll to, the summed height in px of today's row through
     * it, the list's bottom padding in px that ends the scroll with today's
     * row at the top, and why it is that padding.
     */
    data class Target(
        val index: Int,
        val rowsPx: Int,
        val paddingPx: Int,
        val fit: Fit,
    )

    /**
     * The last row, today's or one after it, whose rows from today's on fit
     * into the list, listPx high, by the rows' heights in px: the last row when the
     * rows from today's on fit into listPx; otherwise the last that fits into
     * listPx less extraPx, today's row when it alone does not. extraPx is
     * the room the smooth scroll leaves below a target that is not the
     * list's last row, the fading edge length (D27). The padding is what the
     * rows leave of listPx, so at least extraPx; none for the last row, which
     * the scroll brings to the list's bottom, and none for today's row alone
     * when it is taller than listPx less extraPx. Null when today is no row's
     * index, as -1 from indexOfFirst.
     */
    fun target(
        heightsPx: List<Int>,
        today: Int,
        listPx: Float,
        extraPx: Int,
    ): Target? = if (today in heightsPx.indices) targetFrom(heightsPx, today, listPx, extraPx) else null

    /** The [target] for today, a row's index. */
    private fun targetFrom(
        heightsPx: List<Int>,
        today: Int,
        listPx: Float,
        extraPx: Int,
    ): Target {
        val throughLast = heightsPx.subList(today, heightsPx.size).sum()
        return if (throughLast <= listPx) {
            Target(heightsPx.lastIndex, throughLast, 0, Fit.TO_THE_END)
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
        // Rounded down, so today's row is never cut at the top: rounded up, the padding would scroll it up to 1 px
        // too far.
        return if (rowsPx <= roomPx) {
            Target(index, rowsPx, (listPx - rowsPx).toInt(), Fit.PADDED)
        } else {
            Target(index, rowsPx, 0, Fit.TODAY_TOO_TALL)
        }
    }

    /**
     * Scrolls the list of views to its [target] and sets the list's bottom
     * padding to the target's, with the smooth scroll's room below it from
     * context's fading edge length: heightsPx are the drawn rows' heights,
     * today the index of today's row; the list is the widget's height less
     * its card and a header headerPx high, at scale ([DrawnWidget.listPx]).
     * Without a height, it scrolls today's row only into view, without
     * padding (D27). When no row is today (-1), the list does not scroll and
     * the log warns. Each case sets the list's padding, as an earlier update
     * may have set one: setViewPadding sets all four sides, and the list has
     * no padding but this one. Returns where it scrolls and from what, for
     * the drawing's log line ([DrawnWidget.logDrawn]).
     */
    fun scroll(
        context: Context,
        views: RemoteViews,
        scale: Scale,
        heightsPx: List<Int>,
        today: Int,
        height: WidgetHeight?,
        headerPx: Int,
        tag: String,
        id: Int,
    ): String =
        when {
            today !in heightsPx.indices -> {
                views.setViewPadding(R.id.list, 0, 0, 0, 0)
                Log.w(tag, "Widget $id: no row is today, so its list does not scroll")
                "; no row is today"
            }

            height == null -> {
                views.setViewPadding(R.id.list, 0, 0, 0, 0)
                views.setScrollPosition(R.id.list, today)
                // Not "no appWidgetMaxHeight": views() passes no height either; WidgetHeight.of warns of a launcher's.
                "; today is row $today, scrolls it into view: no height"
            }

            else -> {
                val listPx = DrawnWidget.listPx(scale, height.dp, headerPx)
                val extraPx = ViewConfiguration.get(context).scaledFadingEdgeLength
                val target = targetFrom(heightsPx, today, listPx, extraPx)
                views.setViewPadding(R.id.list, 0, 0, 0, target.paddingPx)
                views.setScrollPosition(R.id.list, target.index)
                reach(target, today, listPx, extraPx) + "; ${height.forLog}"
            }
        }

    /** How far the scroll to target reaches in a list listPx high, and the padding it gets, for [scroll]'s log. */
    private fun reach(
        target: Target,
        today: Int,
        listPx: Float,
        extraPx: Int,
    ): String {
        val count = target.index - today + 1
        val rows = if (count == 1) "1 row" else "$count rows"
        val padding =
            when (target.fit) {
                Fit.PADDED -> {
                    "bottom padding ${target.paddingPx} px, at least the $extraPx px the scroll stops above the bottom"
                }

                Fit.TO_THE_END -> {
                    "no padding: the rows to the end fit"
                }

                Fit.TODAY_TOO_TALL -> {
                    "no padding: today's row is taller than the list less the scroll's $extraPx px"
                }
            }
        return "; today is row $today, scrolls to row ${target.index}, $rows, ${target.rowsPx} of the list's " +
            "${listPx.roundToInt()} px, $padding"
    }
}
