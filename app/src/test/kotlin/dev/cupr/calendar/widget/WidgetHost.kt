package dev.cupr.calendar.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Parcel
import android.util.TypedValue
import android.util.Xml
import android.view.View
import android.view.ViewConfiguration
import android.widget.ListView
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.render.Scale
import org.robolectric.Shadows.shadowOf
import org.xmlpull.v1.XmlPullParser
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A widget as a launcher shows it, by default the month widget at its default
 * size of 4 × 4 cells; views makes a widget's views at a width in dp.
 */
internal object WidgetHost {
    const val WIDTH_DP = DrawnWidget.DEFAULT_WIDTH_DP

    /** 4 cells high (design.md). */
    const val HEIGHT_DP = 456

    /** A step of [scrollToTarget], less than any list's height less its padding in the tests. */
    private const val SCROLL_STEP_PX = 4

    /** 2 cells high (design.md), "Woche quer"'s default. */
    const val LOW_HEIGHT_DP = WeekColumnsWidget.DEFAULT_HEIGHT_DP

    val month: (Context, Int) -> RemoteViews = { context, width -> MonthWidget.views(context, width) }

    /** The widget's views parcelled, as the launcher receives them, then applied in a widget host's view. */
    fun applied(
        context: Context,
        widthDp: Int = WIDTH_DP,
        views: (Context, Int) -> RemoteViews = month,
    ): View = parcelled(views(context, widthDp)).apply(context, AppWidgetHostView(context))

    /**
     * The views made by views at the default width, parcelled, applied
     * again to widget as a launcher does on an update, in a widget host's
     * view, and the widget laid out anew heightDp high.
     */
    fun reapplied(
        context: Context,
        widget: View,
        heightDp: Int = HEIGHT_DP,
        views: (Context, Int) -> RemoteViews,
    ) {
        if (widget.parent == null) AppWidgetHostView(context).addView(widget)
        parcelled(views(context, WIDTH_DP)).reapply(context, widget)
        layOut(context, widget, WIDTH_DP, heightDp)
    }

    private fun parcelled(views: RemoteViews): RemoteViews {
        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return RemoteViews.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    /**
     * The applied widget, drawn at its default width, laid out widthDp wide
     * and heightDp high, so its list holds rows.
     */
    fun laidOut(
        context: Context,
        heightDp: Int = HEIGHT_DP,
        views: (Context, Int) -> RemoteViews = month,
        widthDp: Int = WIDTH_DP,
    ): View {
        val widget = applied(context, WIDTH_DP, views)
        layOut(context, widget, widthDp, heightDp)
        return widget
    }

    private fun layOut(
        context: Context,
        widget: View,
        widthDp: Int,
        heightDp: Int,
    ) {
        val scale = Scale(context.resources.displayMetrics.density)
        widget.measure(exactly(scale, widthDp), exactly(scale, heightDp))
        widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
    }

    /**
     * The list of the widget laid out heightDp high and widthDp wide, from
     * its top scrolled to the row its views scroll to ([scrollToTarget]), and
     * that row.
     */
    fun scrolledToTarget(
        context: Context,
        heightDp: Int,
        views: (Context, Int) -> RemoteViews,
        widthDp: Int = WIDTH_DP,
    ): Pair<ListView, Int> {
        val list = laidOut(context, heightDp, views, widthDp).findViewById<ListView>(R.id.list)
        return list to scrollToTarget(list)
    }

    /**
     * Scrolls list from where it stands to the row its views last scrolled
     * to, and returns that row. Robolectric's shadow records the row but runs
     * no scroll, so this ends the scroll where AOSP's
     * `AbsListView.PositionScroller` ends it, by the rule D27 gives, of which
     * it models two cases: a row already shown with its top in the list, as
     * `scrollToVisible`'s bottom branch moves it, its bottom to the padding
     * when it is below; and a row below the shown ones, as `MOVE_DOWN_POS`
     * does, which brings up the last shown row until it is the target. A row
     * shown with its top cut, which `scrollToVisible` brings to the top, and
     * a row above the shown ones (`MOVE_UP_POS`) fail its check instead. It
     * scrolls in steps of a few px, as the smooth scroll does frame by frame:
     * one step scrolls at most the list's height less its padding
     * (`AbsListView.trackMotionScroll`).
     */
    fun scrollToTarget(list: ListView): Int {
        val target = shadowOf(list).smoothScrolledPosition
        val first = list.firstVisiblePosition
        check(target >= first) { "row $target is above the first shown row $first: MOVE_UP_POS is not modelled" }
        val lastShown = { list.firstVisiblePosition + list.childCount - 1 }
        if (target <= lastShown()) {
            val row = list.getChildAt(target - first)
            check(row.top >= list.paddingTop) {
                "row $target is shown with its top cut: scrollToVisible's top branch is not modelled"
            }
            scrollInSteps(list, max(0, row.bottom - (list.height - list.paddingBottom)))
        } else {
            val fadingEdge = ViewConfiguration.get(list.context).scaledFadingEdgeLength
            do {
                val last = lastShown()
                val room = if (last < list.count - 1) max(list.paddingBottom, fadingEdge) else list.paddingBottom
                scrollInSteps(list, list.getChildAt(list.childCount - 1).bottom - list.height + room)
                // The scroller would wait for a row that never comes; the test fails instead.
                check(last == target || lastShown() > last) { "the scroll to row $target stopped at row $last" }
            } while (last < target)
        }
        return target
    }

    private fun scrollInSteps(
        list: ListView,
        px: Int,
    ) {
        var left = px
        while (left > 0) {
            val step = min(left, SCROLL_STEP_PX)
            list.scrollListBy(step)
            left -= step
        }
    }

    /** An attribute of a widget's provider info (res/xml): a dimension in pixels, or an integer. */
    fun providerAttribute(
        context: Context,
        xml: Int,
        attribute: Int,
    ): Int {
        val parser = context.resources.getXml(xml)
        try {
            while (parser.next() != XmlPullParser.START_TAG) continue
            val array = context.obtainStyledAttributes(Xml.asAttributeSet(parser), intArrayOf(attribute))
            try {
                val value = TypedValue()
                check(array.getValue(0, value)) { "${parser.name} has no attribute $attribute" }
                return if (value.type == TypedValue.TYPE_DIMENSION) {
                    array.getDimensionPixelSize(0, 0)
                } else {
                    array.getInt(0, 0)
                }
            } finally {
                array.recycle()
            }
        } finally {
            parser.close()
        }
    }

    private fun exactly(
        scale: Scale,
        dp: Int,
    ) = View.MeasureSpec.makeMeasureSpec(scale.px(dp.toFloat()).roundToInt(), View.MeasureSpec.EXACTLY)
}
