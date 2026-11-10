package dev.cupr.calendar.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Parcel
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.render.Scale
import kotlin.math.roundToInt

/** The month widget as a launcher shows it, at its default size of 4 × 4 cells unless asked otherwise. */
internal object WidgetHost {
    const val WIDTH_DP = MonthWidget.DEFAULT_WIDTH_DP

    /** 4 cells high (design.md). */
    const val HEIGHT_DP = 456

    /** The widget's views parcelled, as the launcher receives them, then applied in a widget host's view. */
    fun applied(
        context: Context,
        widthDp: Int = WIDTH_DP,
    ): View {
        val parcel = Parcel.obtain()
        try {
            MonthWidget.views(context, widthDp).writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return RemoteViews.CREATOR.createFromParcel(parcel).apply(context, AppWidgetHostView(context))
        } finally {
            parcel.recycle()
        }
    }

    /** The applied widget laid out at its default width and heightDp high, so its list holds rows. */
    fun laidOut(
        context: Context,
        heightDp: Int = HEIGHT_DP,
    ): View {
        val widget = applied(context)
        val scale = Scale(context.resources.displayMetrics.density)
        widget.measure(exactly(scale, WIDTH_DP), exactly(scale, heightDp))
        widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
        return widget
    }

    private fun exactly(
        scale: Scale,
        dp: Int,
    ) = View.MeasureSpec.makeMeasureSpec(scale.px(dp.toFloat()).roundToInt(), View.MeasureSpec.EXACTLY)
}
