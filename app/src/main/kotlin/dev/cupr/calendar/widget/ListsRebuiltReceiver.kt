package dev.cupr.calendar.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Tells the widgets that the system dropped their lists, so the next update
 * builds them anew ([Widgets.listsRebuilt], D59).
 */
class ListsRebuiltReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        causes[intent.action]?.let { Widgets.listsRebuilt(context, it) }
    }

    private companion object {
        /** The broadcasts it takes, each with what the log says happened. */
        val causes =
            mapOf(
                Intent.ACTION_MY_PACKAGE_REPLACED to "The app was updated",
                Intent.ACTION_BOOT_COMPLETED to "The phone was restarted",
            )
    }
}
