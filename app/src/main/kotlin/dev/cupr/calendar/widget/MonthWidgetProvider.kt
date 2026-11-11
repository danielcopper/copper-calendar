package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context

/** The month widget ([DrawnWidgetProvider]). */
class MonthWidgetProvider : DrawnWidgetProvider() {
    override val tag = MonthWidget.TAG

    override fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) = MonthWidget.update(context, manager, id)
}
