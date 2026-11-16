package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.FakeHomeAssistant
import dev.cupr.calendar.ha.RefreshToken
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.signin.SignInActivity
import dev.cupr.calendar.store.AesGcmCipher
import dev.cupr.calendar.store.Snapshot
import dev.cupr.calendar.store.TokenCipher
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import javax.crypto.KeyGenerator

/** The widgets on the home screen: what they show from the app's state, their updates, and midnight (D50–D54). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(context)
    private val shadow = shadowOf(manager)
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var keystoreCipher: () -> TokenCipher
    private val work get() = WorkManager.getInstance(context)

    private val entry =
        Json
            .parseToJsonElement(
                """{"start":"2026-11-12","end":"2026-11-13","all_day":true,"title":"Anna",""" +
                    """"calendar":"calendar.contact_birthdays"}""",
            ).jsonObject

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.WARN)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
    }

    @After
    fun tearDown() {
        Services.cipher = keystoreCipher
        WorkManagerTestInitHelper.closeWorkDatabase()
        home.close()
    }

    /** One widget of each kind, as the launcher adds them, each drawn by its provider's own update. */
    private fun widgets() =
        listOf(
            shadow.createWidget(MonthWidgetProvider::class.java, R.layout.widget_month),
            shadow.createWidget(WeekWidgetProvider::class.java, R.layout.widget_week),
            shadow.createWidget(WeekColumnsWidgetProvider::class.java, R.layout.widget_week_columns),
        )

    private fun signedInWithSnapshot() {
        Services.account(context).signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))
        Services.snapshots(context).write(Snapshot(System.currentTimeMillis(), "Daniel", listOf(entry)))
    }

    private fun plainText(id: Int): TextView? = shadow.getViewFor(id).findViewById(R.id.plain_text)

    private fun hasList(id: Int) = shadow.getViewFor(id).findViewById<View>(R.id.list) is ListView

    private fun assertOpensSignIn(view: View) {
        view.performClick()
        val started = checkNotNull(shadowOf(context).nextStartedActivity)
        assertEquals(SignInActivity::class.java.name, started.component?.className)
        assertEquals(Intent.ACTION_MAIN, started.action)
    }

    @Test
    fun `with no one signed in each widget says so, and a tap opens the sign-in screen`() {
        for (id in widgets()) {
            val text = checkNotNull(plainText(id))
            assertEquals("Nicht angemeldet – zum Anmelden tippen", text.text.toString())
            assertOpensSignIn(text)
        }
    }

    @Test
    fun `signed in before the first snapshot, a tap on the text opens the sign-in screen`() {
        Services.account(context).signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))
        val id = widgets().first()
        val text = checkNotNull(plainText(id))
        assertEquals("Noch keine Daten – zum Öffnen tippen", text.text.toString())
        assertOpensSignIn(text)
    }

    @Test
    fun `the app's update draws the snapshot on every widget and schedules midnight`() {
        val ids = widgets()
        signedInWithSnapshot()

        Widgets.update(context)

        assertTrue(ids.all(::hasList))
        val midnight = work.getWorkInfosForUniqueWork(Midnight.NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, midnight.state)
        assertTrue("${midnight.initialDelayMillis}", midnight.initialDelayMillis in 1..Duration.ofDays(1).toMillis())
    }

    @Test
    fun `a sign-in no longer usable keeps the snapshot drawn, and the header opens the sign-in screen`() {
        val id = widgets().first()
        signedInWithSnapshot()
        Services.account(context).reject()

        Widgets.update(context)

        assertTrue(hasList(id))
        val header = shadow.getViewFor(id).findViewById<View>(R.id.header)
        assertTrue("${header.contentDescription}", header.contentDescription.endsWith(", Neu anmelden"))
        assertOpensSignIn(header)
    }

    @Test
    fun `a stale snapshot's header names when it was fetched and still opens the calendar page`() {
        val stale = SampleData.calendar().copy(notice = Notice.Stale("Stand 08:29"))
        val views =
            listOf(
                MonthWidget.views(context, WidgetHost.WIDTH_DP, calendar = stale),
                WeekWidget.views(context, WidgetHost.WIDTH_DP, calendar = stale),
                WeekColumnsWidget.views(context, WidgetHost.WIDTH_DP, calendar = stale),
            )
        for (drawn in views) {
            val header = WidgetHost.applied(context, views = { _, _ -> drawn }).findViewById<View>(R.id.header)
            assertTrue("${header.contentDescription}", header.contentDescription.endsWith(", Stand 08:29"))
            header.performClick()
            assertEquals(Tap.Header, Taps.read(checkNotNull(shadowOf(context).nextStartedActivity)))
        }
    }

    @Test
    fun `a new sign-in turns every widget to the text that there is no data yet`() {
        val ids = widgets()
        home.token = { FakeHomeAssistant.json("""{"refresh_token":"refresh-1","token_type":"Bearer"}""") }

        assertTrue(Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1")))

        for (id in ids) assertEquals("Noch keine Daten – zum Öffnen tippen", plainText(id)?.text.toString())
    }

    @Test
    fun `signing out turns every widget to the text that no one is signed in`() {
        val ids = widgets()
        signedInWithSnapshot()
        Widgets.update(context)

        Services.signOut(context)

        for (id in ids) assertEquals("Nicht angemeldet – zum Anmelden tippen", plainText(id)?.text.toString())
    }

    @Test
    fun `midnight is due at the start of the next local day`() {
        val zone = ZoneId.of("Europe/Berlin")
        val clock = { time: LocalDateTime -> Clock.fixed(time.atZone(zone).toInstant(), zone) }
        assertEquals(Duration.ofMinutes(30), Midnight.untilMidnight(clock(LocalDateTime.of(2026, 11, 12, 23, 30))))
        assertEquals(Duration.ofDays(1), Midnight.untilMidnight(clock(LocalDateTime.of(2026, 11, 12, 0, 0))))
        // The night clocks go back an hour has 25 hours.
        assertEquals(Duration.ofHours(25), Midnight.untilMidnight(clock(LocalDateTime.of(2026, 10, 25, 0, 0))))
    }

    @Test
    fun `the midnight job redraws the widgets and schedules the next midnight after itself`() {
        val id = widgets().first()
        signedInWithSnapshot()
        Midnight.schedule(context, ExistingWorkPolicy.REPLACE)
        val first = work.getWorkInfosForUniqueWork(Midnight.NAME).get().single().id
        assertTrue(plainText(id) != null)

        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setInitialDelayMet(first)

        assertTrue(hasList(id))
        val infos = work.getWorkInfosForUniqueWork(Midnight.NAME).get().associate { it.id to it.state }
        assertEquals(WorkInfo.State.SUCCEEDED, infos[first])
        assertEquals(listOf(WorkInfo.State.ENQUEUED), infos.filterKeys { it != first }.values.toList())
    }
}
