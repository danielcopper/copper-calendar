package dev.cupr.calendar.widget

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.days.CLOCK_TIME
import dev.cupr.calendar.days.DAY_AND_MONTH
import dev.cupr.calendar.ha.ThemePalettes
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.model.Palettes
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.Snapshot
import kotlinx.serialization.json.JsonObject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * What the widgets show (D2): the snapshot's calendar, or a plain text when
 * there is none to draw (D50).
 */
sealed interface Content {
    /** No calendar to draw: a plain text on the card; a tap on it opens the sign-in screen (D50). */
    data class NoData(
        @param:StringRes val text: Int,
    ) : Content

    /**
     * The calendar to draw: its entries, the palettes to draw them with,
     * today, and what the header shows at its right instead of its range and
     * chevron, none while the snapshot is fresh and the sign-in usable.
     */
    data class Calendar(
        val entries: List<Entry>,
        val palettes: Palettes,
        val today: LocalDate,
        val notice: Notice? = null,
    ) : Content {
        /** This calendar without the entries of the calendars leftOut (D11). */
        fun without(leftOut: Set<String>): Calendar =
            if (leftOut.isEmpty()) this else copy(entries = entries.filter { it.calendar !in leftOut })
    }

    companion object {
        private const val TAG = "Widgets"

        /** How old the last successful fetch may be before the header says when it was (D51). */
        val STALE_AFTER: Duration = Duration.ofHours(1)

        /** What the widgets show now, from the sign-in and the snapshot the app keeps. */
        fun read(context: Context): Content =
            of(context, Services.account(context).state, Services.snapshots(context).read(), Clock.systemDefaultZone())

        /**
         * What the widgets show for state and snapshot at clock's time: signed
         * in without a snapshot, "Noch keine Daten"; without a snapshot
         * otherwise, or signed out, "Nicht angemeldet" (D50); else the
         * snapshot's entries, the problems of reading them again logged, with
         * "Neu anmelden" while the sign-in is no longer usable (D52), else
         * "Stand …" once the snapshot is older than [STALE_AFTER] (D51).
         */
        internal fun of(
            context: Context,
            state: Account.State,
            snapshot: Snapshot?,
            clock: Clock,
        ): Content =
            when {
                snapshot == null && state == Account.State.SIGNED_IN -> NoData(R.string.widget_no_data)
                snapshot == null || state == Account.State.SIGNED_OUT -> NoData(R.string.widget_signed_out)
                else -> calendar(context, state, snapshot, clock)
            }

        /** The snapshot's calendar, with the notice for state at clock's time. */
        private fun calendar(
            context: Context,
            state: Account.State,
            snapshot: Snapshot,
            clock: Clock,
        ): Calendar {
            val parsed = snapshot.parse(clock.zone)
            parsed.problems.forEach { Log.w(TAG, it) }
            val notice =
                when (state) {
                    Account.State.REJECTED, Account.State.UNREADABLE -> {
                        Notice.SignInAgain(context.getString(R.string.widget_sign_in_again))
                    }

                    Account.State.SIGNED_IN, Account.State.SIGNED_OUT -> {
                        stale(context, snapshot.fetchedAt, clock)?.let(Notice::Stale)
                    }
                }
            return Calendar(parsed.entries, palettes(snapshot.theme), LocalDate.now(clock), notice)
        }

        /**
         * "Stand 14:30" when the fetch at was today, "Stand gestern 18:30"
         * yesterday, "Stand 9. Nov." before; none while it is no older than
         * [STALE_AFTER].
         */
        internal fun stale(
            context: Context,
            at: Instant,
            clock: Clock,
        ): String? {
            if (Duration.between(at, clock.instant()) <= STALE_AFTER) return null
            val local = at.atZone(clock.zone)
            val today = LocalDate.now(clock)
            return when (local.toLocalDate()) {
                today -> context.getString(R.string.widget_stale, local.format(CLOCK_TIME))
                today.minusDays(1) -> context.getString(R.string.widget_stale_yesterday, local.format(CLOCK_TIME))
                else -> context.getString(R.string.widget_stale, local.format(DAY_AND_MONTH))
            }
        }

        /**
         * The theme's palettes, or the built-in ones without a theme. A theme
         * kept in the snapshot could be read at its fetch (D48); should it no
         * longer be, the log says so and the built-in ones stand in.
         */
        private fun palettes(theme: JsonObject?): Palettes =
            when (val read = theme?.let(ThemePalettes::read)) {
                null -> {
                    Palettes.BUILT_IN
                }

                is ThemePalettes.Read.Readable -> {
                    read.palettes
                }

                is ThemePalettes.Read.Unreadable -> {
                    Log.w(TAG, "The snapshot's theme cannot be read (${read.reason}); drawing with the built-in ones")
                    Palettes.BUILT_IN
                }
            }
    }
}

/** What a header shows at its right instead of its range and chevron. */
sealed interface Notice {
    val text: String

    /** The last successful fetch is older than an hour: when it was, muted (D51). */
    data class Stale(
        override val text: String,
    ) : Notice

    /**
     * The sign-in is no longer usable: "Neu anmelden" in the accent colour;
     * the header opens the sign-in screen (D52).
     */
    data class SignInAgain(
        override val text: String,
    ) : Notice
}
