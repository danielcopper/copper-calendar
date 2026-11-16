package dev.cupr.calendar.widget

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Palettes
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.Snapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What the widgets show for the sign-in and the snapshot (D50, D51, D52). */
@RunWith(RobolectricTestRunner::class)
class ContentTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = LocalDateTime.of(2026, 11, 12, 9, 30)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val entry =
        json("""{"start":"2026-11-12","end":"2026-11-13","all_day":true,"title":"Anna","calendar":"calendar.contact_birthdays"}""")

    private fun snapshot(
        fetchedAt: LocalDateTime = now.minusMinutes(5),
        entries: List<JsonObject> = listOf(entry),
        theme: JsonObject? = null,
    ) = Snapshot(fetchedAt.atZone(zone).toInstant().toEpochMilli(), "Daniel", entries, theme)

    private fun of(
        state: Account.State,
        snapshot: Snapshot?,
    ) = Content.of(context, state, snapshot, clock)

    private fun calendar(
        state: Account.State = Account.State.SIGNED_IN,
        snapshot: Snapshot = snapshot(),
    ) = of(state, snapshot) as Content.Calendar

    @Test
    fun `signed in before the first snapshot, the widget says there is no data yet`() {
        assertEquals(Content.NoData(R.string.widget_no_data), of(Account.State.SIGNED_IN, null))
        assertEquals("Noch keine Daten – zum Öffnen tippen", context.getString(R.string.widget_no_data))
    }

    @Test
    fun `signed out, or without a snapshot and a usable sign-in, the widget says no one is signed in`() {
        val signedOut = Content.NoData(R.string.widget_signed_out)
        assertEquals(signedOut, of(Account.State.SIGNED_OUT, null))
        assertEquals(signedOut, of(Account.State.SIGNED_OUT, snapshot()))
        assertEquals(signedOut, of(Account.State.REJECTED, null))
        assertEquals(signedOut, of(Account.State.UNREADABLE, null))
        assertEquals("Nicht angemeldet – zum Anmelden tippen", context.getString(R.string.widget_signed_out))
    }

    @Test
    fun `a fresh snapshot draws its entries on today with the built-in palettes and no notice`() {
        val calendar = calendar()
        assertEquals(listOf("Anna"), calendar.entries.map { it.title })
        assertEquals(LocalDate.of(2026, 11, 12), calendar.today)
        assertEquals(Palettes.BUILT_IN, calendar.palettes)
        assertNull(calendar.notice)
    }

    @Test
    fun `the snapshot's theme gives the palettes`() {
        val calendar = calendar(snapshot = snapshot(theme = json("""{"primary-color":"#000001"}""")))
        assertEquals(0xFF000001.toInt(), calendar.palettes.light.accent)
        assertEquals(0xFF000001.toInt(), calendar.palettes.dark.accent)
    }

    @Test
    fun `a snapshot's theme that can no longer be read draws with the built-in palettes, logged`() {
        val calendar = calendar(snapshot = snapshot(theme = json("""{"modes":3}""")))
        assertEquals(Palettes.BUILT_IN, calendar.palettes)
        assertEquals(
            "The snapshot's theme cannot be read (modes is not an object); drawing with the built-in colours",
            ShadowLog.getLogsForTag("Widgets").single().msg,
        )
    }

    @Test
    fun `an entry that cannot be read again is left out and logged`() {
        val broken = json("""{"start":"2026-11-12","all_day":true,"title":"Broken","calendar":"c"}""")
        val calendar = calendar(snapshot = snapshot(entries = listOf(entry, broken)))
        assertEquals(listOf("Anna"), calendar.entries.map { it.title })
        val logged = ShadowLog.getLogsForTag("Widgets").single().msg
        assertTrue(logged, logged.startsWith("Entry 1 (\"Broken\") left out:"))
    }

    @Test
    fun `a snapshot an hour old is fresh, a minute older says when it was fetched`() {
        assertNull(calendar(snapshot = snapshot(now.minusHours(1))).notice)
        assertEquals(Notice.Stale("Stand 08:29"), calendar(snapshot = snapshot(now.minusMinutes(61))).notice)
    }

    @Test
    fun `a snapshot of yesterday says yesterday and its time, an older one its day`() {
        val yesterday = LocalDateTime.of(2026, 11, 11, 18, 30)
        assertEquals(Notice.Stale("Stand gestern 18:30"), calendar(snapshot = snapshot(yesterday)).notice)
        val older = LocalDateTime.of(2026, 11, 9, 18, 30)
        assertEquals(Notice.Stale("Stand 9. Nov."), calendar(snapshot = snapshot(older)).notice)
    }

    @Test
    fun `a sign-in no longer usable keeps drawing the snapshot and asks to sign in again, also when it is old`() {
        for (state in listOf(Account.State.REJECTED, Account.State.UNREADABLE)) {
            val calendar = calendar(state, snapshot(LocalDateTime.of(2026, 11, 9, 18, 30)))
            assertEquals(listOf("Anna"), calendar.entries.map { it.title })
            assertEquals(Notice.SignInAgain("Neu anmelden"), calendar.notice)
        }
    }
}
