package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

/**
 * Why a fetch failed, as the sign-in screen names it in plain words (D46;
 * which failure is which: `ha.FailureKind` and `fetch.Fetcher`).
 */
enum class FetchCause(
    override val stored: String,
) : StoredChoice {
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
        if (at < 0 || !prefs.contains(CAUSE)) return null
        val cause = prefs.choice(CAUSE, FetchCause.entries, FetchCause.UNEXPECTED, TAG, "A failed fetch's cause")
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
