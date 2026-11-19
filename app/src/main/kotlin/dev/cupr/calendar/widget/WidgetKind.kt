package dev.cupr.calendar.widget

import android.content.ComponentName
import android.content.Context
import androidx.annotation.StringRes
import dev.cupr.calendar.R

/**
 * The app's three widgets: the name the widget picker gives each, and which
 * of the settings each has, beside the calendars, the corner of "+" and the
 * taps on the header, a day and "+", which all have: the week widgets their
 * days (D20), the month and "Woche" when they return to today (D27), "Woche"
 * the tap on an entry, the one widget with entry areas (D37).
 */
enum class WidgetKind(
    @param:StringRes val label: Int,
    val days: Boolean,
    val backToToday: Boolean,
    val entryTaps: Boolean,
) {
    MONTH(R.string.widget_month_label, days = false, backToToday = true, entryTaps = false),
    WEEK(R.string.widget_week_label, days = true, backToToday = true, entryTaps = true),
    WEEK_COLUMNS(R.string.widget_week_columns_label, days = true, backToToday = false, entryTaps = false),
    ;

    companion object {
        /** The kind of a widget of provider; none for a provider that is not one of this app's ([Widgets.provider]). */
        fun of(
            context: Context,
            provider: ComponentName,
        ): WidgetKind? = Widgets.provider(context, provider)?.kind
    }
}
