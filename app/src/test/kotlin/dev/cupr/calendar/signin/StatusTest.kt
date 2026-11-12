package dev.cupr.calendar.signin

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.FailedFetch
import dev.cupr.calendar.store.FetchCause
import dev.cupr.calendar.store.Snapshot
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class StatusTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-11-12T13:45:00Z")
    private val today = Instant.parse("2026-11-12T13:30:00Z")
    private val yesterday = Instant.parse("2026-11-11T17:30:00Z")

    private fun status(
        state: Account.State,
        fetchedAt: Instant? = null,
        failed: FailedFetch? = null,
    ) = Status(state, "https://home.example", fetchedAt?.let { LastFetch("Daniel", it) }, failed)

    @Test
    fun `signed in, the line names the user and today's fetch time`() {
        assertEquals(
            "Angemeldet als Daniel, letzter Abruf um 14:30",
            status(Account.State.SIGNED_IN, today).line(context, now, berlin),
        )
    }

    @Test
    fun `a fetch on an earlier day is named with its date`() {
        assertEquals(
            "Angemeldet als Daniel, letzter Abruf am 11. November um 18:30",
            status(Account.State.SIGNED_IN, yesterday).line(context, now, berlin),
        )
    }

    @Test
    fun `signed in without a fetch yet, the line says so`() {
        assertEquals("Angemeldet, noch kein Abruf", status(Account.State.SIGNED_IN).line(context, now, berlin))
    }

    @Test
    fun `a refused and an unreadable sign-in each say why signing in again is needed`() {
        assertEquals(
            "Home Assistant hat die Anmeldung abgelehnt – bitte neu anmelden",
            status(Account.State.REJECTED).line(context, now, berlin),
        )
        assertEquals(
            "Gespeicherte Anmeldung auf diesem Handy nicht lesbar – bitte neu anmelden",
            status(Account.State.UNREADABLE).line(context, now, berlin),
        )
        assertNull(status(Account.State.SIGNED_OUT).line(context, now, berlin))
    }

    @Test
    fun `while the last fetch failed, a second line says when and why`() {
        val failedToday = FailedFetch(today, FetchCause.UNEXPECTED)
        val failedYesterday = FailedFetch(yesterday, FetchCause.NO_NETWORK)

        assertEquals(
            "Letzter Abruf fehlgeschlagen um 14:30: unerwartete Antwort",
            status(Account.State.SIGNED_IN, failed = failedToday).failedLine(context, now, berlin),
        )
        assertEquals(
            "Letzter Abruf fehlgeschlagen am 11. November um 18:30: kein Netz",
            status(Account.State.SIGNED_IN, failed = failedYesterday).failedLine(context, now, berlin),
        )
        assertNull(status(Account.State.SIGNED_IN, today).failedLine(context, now, berlin))
        assertNull(status(Account.State.REJECTED, failed = failedToday).failedLine(context, now, berlin))
    }

    @Test
    fun `each cause is named in plain words`() {
        val words =
            FetchCause.entries.associateWith {
                status(Account.State.SIGNED_IN, failed = FailedFetch(today, it))
                    .failedLine(context, now, berlin)!!
                    .substringAfter("14:30: ")
            }

        assertEquals(
            mapOf(
                FetchCause.UNREACHABLE to "Home Assistant nicht erreichbar",
                FetchCause.NO_NETWORK to "kein Netz",
                FetchCause.TIMEOUT to "Zeitüberschreitung",
                FetchCause.SENSOR_MISSING to "Sensor nicht gefunden",
                FetchCause.REFUSED to "Anmeldung abgelehnt",
                FetchCause.UNEXPECTED to "unerwartete Antwort",
                FetchCause.PHONE to "Fehler auf diesem Handy",
            ),
            words,
        )
    }

    @Test
    fun `the status reads the address without its slash, the snapshot's user and time, and the last failure`() {
        Services.account(context).rememberAddress(HaAddress("https://home.example/".toHttpUrl()))
        Services.snapshots(context).write(Snapshot(1_000, "Daniel", emptyList()))
        Services
            .failures(
                context,
            ).write(FailedFetch(Instant.ofEpochMilli(2_000), FetchCause.UNEXPECTED))

        assertEquals(
            Status(
                Account.State.SIGNED_OUT,
                "https://home.example",
                LastFetch("Daniel", Instant.ofEpochMilli(1_000)),
                FailedFetch(Instant.ofEpochMilli(2_000), FetchCause.UNEXPECTED),
            ),
            Status.read(context),
        )
    }
}
