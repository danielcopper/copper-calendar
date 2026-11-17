package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit

/** How often the app fetches from Home Assistant, set once for the app (D3, D46). */
enum class FetchInterval(
    val minutes: Long,
    override val stored: String,
) : StoredChoice {
    /** WorkManager's least interval for periodic work, the default. */
    EVERY_15_MINUTES(15, "15"),
    EVERY_30_MINUTES(30, "30"),
    EVERY_60_MINUTES(60, "60"),
}

/** The settings set once for the app, not per widget; signing out keeps them. */
class AppSettingsStore(
    private val prefs: SharedPreferences,
) {
    /** The fetch interval; one stored under a name this app does not know reads as the default, logged. */
    fun fetchInterval(): FetchInterval =
        prefs.choice(
            FETCH_INTERVAL,
            FetchInterval.entries,
            FetchInterval.EVERY_15_MINUTES,
            TAG,
            "The stored fetch interval",
        )

    fun writeFetchInterval(interval: FetchInterval) = prefs.edit { putString(FETCH_INTERVAL, interval.stored) }

    private companion object {
        const val TAG = "AppSettings"
        const val FETCH_INTERVAL = "fetch_interval"
    }
}
