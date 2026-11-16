package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.fetch.FetchWorker
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import kotlin.concurrent.thread

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
        // The night the clocks go back has 25 hours.
        assertTrue("${midnight.initialDelayMillis}", midnight.initialDelayMillis in 1..Duration.ofHours(25).toMillis())
    }

    @Test
    fun `the launcher's own updates draw the widgets and leave WorkManager alone`() {
        signedInWithSnapshot()
        val ids = widgets()
        MonthWidgetProvider().onUpdate(context, manager, intArrayOf(ids.first()))
        MonthWidgetProvider().onAppWidgetOptionsChanged(context, manager, ids.first(), Bundle())

        assertTrue(ids.all(::hasList))
        val queued = work.getWorkInfos(WorkQuery.fromStates(WorkInfo.State.entries)).get()
        assertEquals(emptyList<WorkInfo>(), queued)
    }

    @Test
    fun `a fetch redraws the widgets, from no data yet to the calendar`() {
        Services.account(context).signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))
        val ids = widgets()
        assertTrue(ids.all { plainText(it)?.text.toString() == "Noch keine Daten – zum Öffnen tippen" })

        val fetched = TestListenableWorkerBuilder<FetchWorker>(context).build().doWork()

        assertEquals(ListenableWorker.Result.success(), fetched)

        assertTrue(ids.all(::hasList))
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

    /**
     * An update of the app that read before a sign-out and waits there, while
     * second, an update after the sign-out, reads that no one is signed in:
     * second either waits for it or, unserialised, finishes before it.
     */
    private fun afterReadBeforeSignOut(second: () -> Unit) {
        val read = CountDownLatch(1)
        val release = CountDownLatch(1)
        val content = DrawnWidgetProvider.content
        DrawnWidgetProvider.content = {
            if (Thread.currentThread().name == "before") {
                read.countDown()
                release.await()
                Content.NoData(R.string.widget_no_data)
            } else {
                Content.NoData(R.string.widget_signed_out)
            }
        }
        try {
            val before = thread(name = "before") { Widgets.redraw(context) }
            assertTrue(read.await(5, TimeUnit.SECONDS))
            val after = thread(name = "after") { second() }
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (after.isAlive && after.state != Thread.State.BLOCKED && System.nanoTime() < until) {
                Thread.yield()
            }
            release.countDown()
            before.join()
            after.join()
        } finally {
            DrawnWidgetProvider.content = content
        }
    }

    @Test
    fun `an update that read before a sign-out hands its views over before the sign-out's update`() {
        val id = widgets().first()

        afterReadBeforeSignOut { Widgets.redraw(context) }

        assertEquals("Nicht angemeldet – zum Anmelden tippen", plainText(id)?.text.toString())
    }

    @Test
    fun `the launcher's update waits for an update of the app that is under way`() {
        val id = widgets().first()

        afterReadBeforeSignOut { MonthWidgetProvider().onUpdate(context, manager, intArrayOf(id)) }

        assertEquals("Nicht angemeldet – zum Anmelden tippen", plainText(id)?.text.toString())
    }

    @Test
    fun `a midnight whose redraw fails logs it and still schedules the next midnight`() {
        val content = DrawnWidgetProvider.content
        val failure = IllegalStateException("the widgets cannot be read")
        DrawnWidgetProvider.content = { throw failure }
        try {
            Midnight.schedule(context, ExistingWorkPolicy.REPLACE)
            val first =
                work
                    .getWorkInfosForUniqueWork(Midnight.NAME)
                    .get()
                    .single()
                    .id

            checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setInitialDelayMet(first)

            val infos = work.getWorkInfosForUniqueWork(Midnight.NAME).get().associate { it.id to it.state }
            assertEquals(WorkInfo.State.SUCCEEDED, infos[first])
            assertEquals(listOf(WorkInfo.State.ENQUEUED), infos.filterKeys { it != first }.values.toList())
            val logged = ShadowLog.getLogsForTag("Widgets").filter { it.type == Log.ERROR }
            assertSame(failure, logged.single().throwable)
        } finally {
            DrawnWidgetProvider.content = content
        }
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
        val first =
            work
                .getWorkInfosForUniqueWork(Midnight.NAME)
                .get()
                .single()
                .id
        assertTrue(plainText(id) != null)

        checkNotNull(WorkManagerTestInitHelper.getTestDriver(context)).setInitialDelayMet(first)

        assertTrue(hasList(id))
        val infos = work.getWorkInfosForUniqueWork(Midnight.NAME).get().associate { it.id to it.state }
        assertEquals(WorkInfo.State.SUCCEEDED, infos[first])
        assertEquals(listOf(WorkInfo.State.ENQUEUED), infos.filterKeys { it != first }.values.toList())
    }
}
