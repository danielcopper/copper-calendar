package dev.cupr.calendar.setup

import android.content.Context
import android.util.Log
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.widget.WidgetKind
import dev.cupr.calendar.widget.Widgets

private const val TAG = "Setup"

/**
 * Stores settings for the widget id, a widget of kind, and redraws it at
 * once ([Widgets.saved]). When id is no longer such a widget, removed while
 * its setup was open, nothing is redrawn, the settings are not kept and the
 * log says so; false then.
 */
internal fun save(
    context: Context,
    id: Int,
    kind: WidgetKind,
    settings: WidgetSettings,
): Boolean {
    val store = Settings.widgets(context)
    // Written before the check: a widget removed after it loses them with its removal (onDeleted), one removed
    // before it here. A widget removed during the redraw can keep its list's state and last scroll, which stay
    // behind unread.
    store.write(id, settings)
    if (Widgets.saved(context, id, kind)) return true
    store.remove(intArrayOf(id))
    Log.w(TAG, "Widget $id: saved, but no longer one of the app's widgets, so nothing is redrawn or kept")
    return false
}
