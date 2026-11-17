package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.widget.RemoteViews
import dev.cupr.calendar.R
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAppWidgetManager

/**
 * A widget manager that refuses every drawing as the system does one over
 * its bitmap limit, by an IllegalArgumentException, and takes plain views.
 */
@Implements(AppWidgetManager::class)
class RefusingWidgetManager : ShadowAppWidgetManager() {
    @Implementation
    override fun updateAppWidget(
        appWidgetId: Int,
        views: RemoteViews,
    ) {
        require(views.layoutId == R.layout.widget_plain) {
            "RemoteViews for widget update exceeds maximum bitmap memory usage"
        }
        super.updateAppWidget(appWidgetId, views)
    }
}
