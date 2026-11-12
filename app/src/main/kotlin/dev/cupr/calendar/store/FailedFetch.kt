package dev.cupr.calendar.store

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import java.time.Instant

/**
 * Why a fetch failed, as the sign-in screen names it in plain words (D46;
 * which failure is which: `ha.FailureKind` and `fetch.Fetcher`). [stored]
 * is the name kept in the preferences, fixed apart from the enum's names.
 */
enum class FetchCause(
    val stored: String,
) {
    UNREACHABLE("unreachable"),
    NO_NETWORK("no_network"),
    TIMEOUT("timeout"),
    SENSOR_MISSING("sensor_missing"),
    REFUSED("refused"),
    UNEXPECTED("unexpected"),
    PHONE("phone"),
}

/** The last fetch, when it failed: when, and its [cause] for the screen; the log keeps its technical reason (D46). */
data class FailedFetch(
    val at: Instant,
    val cause: FetchCause,
)

/** Keeps the last failed fetch until a fetch succeeds or the sign-in changes (D46). */
class FailedFetchStore(
    private val prefs: SharedPreferences,
) {
    /** The last failed fetch; a cause stored under a name this app does not know reads as unexpected, logged. */
    fun read(): FailedFetch? {
        val at = prefs.getLong(AT, -1)
        val name = prefs.getString(CAUSE, null)
        if (at < 0 || name == null) return null
        val cause =
            FetchCause.entries.firstOrNull { it.stored == name }
                ?: FetchCause.UNEXPECTED.also { Log.w(TAG, "A failed fetch's cause \"$name\" is unknown") }
        return FailedFetch(Instant.ofEpochMilli(at), cause)
    }

    fun write(failed: FailedFetch) =
        prefs.edit {
            putLong(AT, failed.at.toEpochMilli())
            putString(CAUSE, failed.cause.stored)
        }

    fun clear() =
        prefs.edit {
            remove(AT)
            remove(CAUSE)
            remove(OLD_DETAIL)
        }

    private companion object {
        const val TAG = "FailedFetch"
        const val AT = "failed_at"
        const val CAUSE = "failed_cause"

        /** The technical reason an earlier build stored beside the cause; nothing reads it. */
        const val OLD_DETAIL = "failed_detail"
    }
}
