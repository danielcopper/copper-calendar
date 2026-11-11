package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/** The week widget "Woche" ([DrawnWidgetProvider]). */
class WeekWidgetProvider : DrawnWidgetProvider() {
    override val tag = "WeekWidgetProvider"

    override fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) = WeekWidget.update(context, manager, id)
}
