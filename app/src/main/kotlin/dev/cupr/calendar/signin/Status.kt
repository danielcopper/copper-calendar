package dev.cupr.calendar.signin

import android.content.Context
import androidx.annotation.StringRes
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.days.CLOCK_TIME
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.FailedFetch
import dev.cupr.calendar.store.FetchCause
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The snapshot's fetch: whose, and when. */
data class LastFetch(
    val user: String,
    val at: Instant,
)

/** What the sign-in screen shows about the sign-in and the fetches. */
data class Status(
    val state: Account.State,
    /**
     * The stored address, the instance's root without its slash, for the
     * field; empty until an address is first entered.
     */
    val address: String,
    val lastFetch: LastFetch?,
    /** The last fetch, while it failed (D46). */
    val failed: FailedFetch?,
) {
    /** The line under the title: who is signed in and when the last fetch was, or why signing in again is needed. */
    fun line(
        context: Context,
        now: Instant,
        zone: ZoneId,
    ): String? =
        when (state) {
            Account.State.SIGNED_OUT -> null
            Account.State.REJECTED -> context.getString(R.string.sign_in_rejected)
            Account.State.UNREADABLE -> context.getString(R.string.sign_in_unreadable)
            Account.State.SIGNED_IN -> signedIn(context, now, zone)
        }

    private fun signedIn(
        context: Context,
        now: Instant,
        zone: ZoneId,
    ): String =
        lastFetch?.let {
            at(context, it.at, now, zone, R.string.sign_in_status_today, R.string.sign_in_status_day, it.user)
        } ?: context.getString(R.string.sign_in_no_fetch)

    /** The line under it while signed in and the last fetch failed: when, and its cause. */
    fun failedLine(
        context: Context,
        now: Instant,
        zone: ZoneId,
    ): String? =
        failed?.takeIf { state == Account.State.SIGNED_IN }?.let {
            val cause = context.getString(it.cause.text)
            at(context, it.at, now, zone, R.string.fetch_failed_today, R.string.fetch_failed_day, cause)
        }

    /** [today]'s text with [subject] and the time when [at] is today, else [day]'s with its date as well. */
    private fun at(
        context: Context,
        at: Instant,
        now: Instant,
        zone: ZoneId,
        @StringRes today: Int,
        @StringRes day: Int,
        subject: String,
    ): String {
        val local = at.atZone(zone)
        val time = local.format(CLOCK_TIME)
        return if (local.toLocalDate() == now.atZone(zone).toLocalDate()) {
            context.getString(today, subject, time)
        } else {
            context.getString(day, subject, time, local.format(DAY))
        }
    }

    /** The cause in plain words (D46); the log names the technical one. */
    private val FetchCause.text: Int
        get() =
            when (this) {
                FetchCause.UNREACHABLE -> R.string.fetch_cause_unreachable
                FetchCause.NO_NETWORK -> R.string.fetch_cause_no_network
                FetchCause.TIMEOUT -> R.string.fetch_cause_timeout
                FetchCause.SENSOR_MISSING -> R.string.fetch_cause_sensor_missing
                FetchCause.REFUSED -> R.string.fetch_cause_refused
                FetchCause.UNEXPECTED -> R.string.fetch_cause_unexpected
                FetchCause.PHONE -> R.string.fetch_cause_phone
            }

    companion object {
        private val DAY = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

        /** Reads the account, the snapshot and the last failure; off the main thread, the snapshot is a file. */
        fun read(context: Context): Status {
            val account = Services.account(context)
            val snapshot = Services.snapshots(context).read()
            return Status(
                state = account.state,
                address =
                    account.address
                        ?.url
                        ?.toString()
                        ?.removeSuffix("/")
                        .orEmpty(),
                lastFetch = snapshot?.let { LastFetch(it.user, it.fetchedAt) },
                failed = Services.failures(context).read(),
            )
        }
    }
}
