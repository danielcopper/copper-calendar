package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

/**
 * When the app last scrolled each widget's list to today, by the widget's
 * id, which "Zurück zu heute" every 15, 30 or 60 minutes counts from (D27),
 * until the widget is removed.
 */
class LastScrollStore(
    private val prefs: SharedPreferences,
) {
    fun read(id: Int): Instant? = prefs.getLong(key(id), -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    fun write(
        id: Int,
        at: Instant,
    ) = prefs.edit { putLong(key(id), at.toEpochMilli()) }

    /** Forgets the widgets ids, removed from the home screen. */
    fun remove(ids: IntArray) = prefs.edit { ids.forEach { remove(key(it)) } }

    private companion object {
        fun key(id: Int) = "$id.scrolled_at"
    }
}
