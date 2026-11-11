package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/** The week widget "Woche quer" ([DrawnWidgetProvider]). */
class WeekColumnsWidgetProvider : DrawnWidgetProvider() {
    override val tag = "WeekColumnsWidgetProvider"

    override fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) = WeekColumnsWidget.update(context, manager, id)
}
