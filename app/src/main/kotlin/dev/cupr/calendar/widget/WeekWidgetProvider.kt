package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/** The week widget "Woche" ([DrawnWidgetProvider]). */
class WeekWidgetProvider : DrawnWidgetProvider() {
    override val kind = WidgetKind.WEEK
    override val tag = WeekWidget.TAG

    override fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        calendar: Content.Calendar,
        look: Look,
    ) = WeekWidget.update(context, manager, id, calendar, look)
}
