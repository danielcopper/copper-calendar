package dev.cupr.calendar.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Parcel
import android.util.TypedValue
import android.util.Xml
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.render.Scale
import org.xmlpull.v1.XmlPullParser
import kotlin.math.roundToInt

/**
 * A widget as a launcher shows it, by default the month widget at its default
 * size of 4 × 4 cells; views makes a widget's views at a width in dp.
 */
internal object WidgetHost {
    const val WIDTH_DP = DrawnWidget.DEFAULT_WIDTH_DP

    /** 4 cells high (design.md). */
    const val HEIGHT_DP = 456

    /** 2 cells high (design.md), "Woche quer"'s default. */
    const val LOW_HEIGHT_DP = WeekColumnsWidget.DEFAULT_HEIGHT_DP

    val month: (Context, Int) -> RemoteViews = { context, width -> MonthWidget.views(context, width) }

    /** The widget's views parcelled, as the launcher receives them, then applied in a widget host's view. */
    fun applied(
        context: Context,
        widthDp: Int = WIDTH_DP,
        views: (Context, Int) -> RemoteViews = month,
    ): View {
        val parcel = Parcel.obtain()
        try {
            views(context, widthDp).writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return RemoteViews.CREATOR.createFromParcel(parcel).apply(context, AppWidgetHostView(context))
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
        val scale = Scale(context.resources.displayMetrics.density)
        widget.measure(exactly(scale, widthDp), exactly(scale, heightDp))
        widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
        return widget
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
