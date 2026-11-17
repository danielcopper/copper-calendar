package dev.cupr.calendar.app

import android.content.Context
import dev.cupr.calendar.store.AppSettingsStore
import dev.cupr.calendar.store.ListStore
import dev.cupr.calendar.store.WidgetSettingsStore

/** What the app keeps besides the sign-in and the snapshot ([Services]): the settings and each widget's list. */
object Settings {
    private const val WIDGETS = "widgets"
    private const val APP = "settings"

    /** Each widget's settings from its setup (D57). */
    fun widgets(context: Context) = WidgetSettingsStore(widgetPrefs(context))

    /** Where each widget's list stands and when it last scrolled to today, beside its settings (D59). */
    fun lists(context: Context) = ListStore(widgetPrefs(context))

    /** The settings set once for the app, which signing out keeps (D60). */
    fun app(context: Context) = AppSettingsStore(context.getSharedPreferences(APP, Context.MODE_PRIVATE))

    private fun widgetPrefs(context: Context) = context.getSharedPreferences(WIDGETS, Context.MODE_PRIVATE)
}
