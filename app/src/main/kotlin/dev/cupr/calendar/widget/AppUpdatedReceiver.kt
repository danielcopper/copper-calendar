package dev.cupr.calendar.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Tells the widgets that the app was updated, which builds their lists anew ([Widgets.appUpdated]). */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Widgets.appUpdated(context)
    }
}
