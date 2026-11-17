package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

/**
 * What the next update of the month's or "Woche"'s list does, by where the
 * list stands since the app last learned it was dropped (D59).
 */
enum class ListState(
    override val stored: String,
) : StoredChoice {
    /** The next update builds the list anew (D59). */
    TO_BUILD("to_build"),

    /** The last update built the list anew; the next one scrolls it to today, whatever is set. */
    TO_SCROLL("to_scroll"),

    /** The list scrolled to today since it was built; "Zurück zu heute" decides. */
    SETTLED("settled"),
}

/**
 * Each widget's list by the widget's id: when the app last scrolled it to
 * today, which "Zurück zu heute" every 15, 30 or 60 minutes counts from, and
 * where it stands since it was built ([ListState]) (D59).
 */
class ListStore(
    private val prefs: SharedPreferences,
) {
    /** When the widget's list last scrolled to today; none before its first scroll since it was built. */
    fun lastScroll(id: Int): Instant? = prefs.getLong(key(id, AT), -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    /**
     * Where the widget's list stands: to build when nothing is kept; a state
     * stored under a name this app does not know reads so too, logged.
     */
    fun state(id: Int): ListState =
        prefs.choice(
            key(id, STATE),
            ListState.entries,
            ListState.TO_BUILD,
            TAG,
            "Widget $id: its list's stored state",
        )

    /** Keeps where the widget's list stands after an update, and scrolledAt as its last scroll when given. */
    fun keep(
        id: Int,
        state: ListState,
        scrolledAt: Instant?,
    ) = prefs.edit {
        putString(key(id, STATE), state.stored)
        scrolledAt?.let { putLong(key(id, AT), it.toEpochMilli()) }
    }

    /**
     * Forgets the lists of the widgets' ids, when they are removed or their
     * lists dropped (D59); until a state is kept again, a list reads to build
     * ([ListState.TO_BUILD]).
     */
    fun forget(ids: IntArray) =
        prefs.edit {
            for (id in ids) {
                remove(key(id, AT))
                remove(key(id, STATE))
            }
        }

    private companion object {
        const val TAG = "ListStore"
        const val AT = "scrolled_at"
        const val STATE = "list"

        fun key(
            id: Int,
            field: String,
        ) = "$id.$field"
    }
}
