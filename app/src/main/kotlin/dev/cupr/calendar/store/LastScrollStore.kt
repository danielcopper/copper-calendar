package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

/**
 * Where the month's or "Woche"'s list stands since the launcher last built
 * it anew (D59). A list built anew shows its first row, and a scroll in the
 * update that builds it does nothing (D27), so the update after that scrolls
 * to today, whatever the widget's "Zurück zu heute" says.
 */
enum class ListState(
    override val stored: String,
) : StoredChoice {
    /**
     * The next update of the list builds it anew: the widget shows plain
     * views, the app was updated, or the widget is new.
     */
    BUILT_ANEW("built_anew"),

    /** The last update built the list anew; the next one scrolls to today. */
    FIRST_SCROLL("first_scroll"),

    /** The list scrolled to today since it was built; "Zurück zu heute" decides. */
    SETTLED("settled"),
}

/**
 * Each widget's list by the widget's id: when the app last scrolled it to
 * today, which "Zurück zu heute" every 15, 30 or 60 minutes counts from, and
 * where it stands since it was built ([ListState]) (D59); forgotten when the
 * list is built anew and when the widget is removed.
 */
class LastScrollStore(
    private val prefs: SharedPreferences,
) {
    /** When the widget's list last scrolled to today; none before its first scroll since it was built. */
    fun read(id: Int): Instant? = prefs.getLong(key(id, AT), -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)

    /**
     * Where the widget's list stands: built anew when nothing is kept; a
     * state stored under a name this app does not know reads so too, logged.
     */
    fun state(id: Int): ListState =
        prefs.choice(
            key(id, STATE),
            ListState.entries,
            ListState.BUILT_ANEW,
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
     * Forgets the lists of the widgets ids: removed from the home screen,
     * showing plain views, or the app updated; a list kept again is built
     * anew.
     */
    fun forget(ids: IntArray) =
        prefs.edit {
            for (id in ids) {
                remove(key(id, AT))
                remove(key(id, STATE))
            }
        }

    private companion object {
        const val TAG = "LastScroll"
        const val AT = "scrolled_at"
        const val STATE = "list"

        fun key(
            id: Int,
            field: String,
        ) = "$id.$field"
    }
}
