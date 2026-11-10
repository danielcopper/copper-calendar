package dev.cupr.calendar.tap

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TapTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val day = LocalDate.of(2026, 11, 12)
    private val calendar = "homeassistant://navigate/dashboard-zuhause/kalender"

    @Test
    fun `the header opens the calendar page`() {
        assertEquals(calendar, TapTargets.uri(Tap.Header))
    }

    @Test
    fun `a day opens the calendar page on that day`() {
        assertEquals("$calendar?day=2026-11-12", TapTargets.uri(Tap.Day(day)))
    }

    @Test
    fun `plus opens the form for a new entry on the day it is given`() {
        assertEquals("$calendar?new=2026-11-12", TapTargets.uri(Tap.New(day)))
    }

    @Test
    fun `a target is its element's template with the day filled in`() {
        assertEquals("$calendar?day={day}", TapTargets.defaults[TapElement.DAY])
        assertEquals("other://x/2026-11-12/y", TapTargets.uri(Tap.New(day), "other://x/{day}/y"))
    }

    @Test
    fun `the tap activity opens the tapped day's target and finishes`() {
        val activity = launch(Taps.fillIn(Tap.Day(day)))
        val opened = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("$calendar?day=2026-11-12", opened.dataString)
        assertTrue(activity.isFinishing)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun `a target opens in a new, cleared task, so Home Assistant keeps one calendar page`() {
        launch(Taps.fillIn(Tap.Header))
        val flags = shadowOf(application).nextStartedActivity.flags
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    @Test
    fun `the fixed parts' pending intents carry their tap to the tap activity`() {
        // Both made before either is read, so the test sees it if the second overwrites the first.
        val header = Taps.pendingIntent(application, Tap.Header)
        val plus = Taps.pendingIntent(application, Tap.New(day))
        val headerIntent = shadowOf(header).savedIntent
        assertEquals(TapActivity::class.java.name, headerIntent.component?.className)
        assertEquals(Tap.Header, Taps.read(headerIntent))
        assertEquals(Tap.New(day), Taps.read(shadowOf(plus).savedIntent))
    }

    @Test
    fun `nothing crashes when no app handles the target`() {
        shadowOf(application).checkActivities(true)
        val activity = launch(Taps.fillIn(Tap.New(day)))
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `an intent without a complete tap opens nothing and is logged with what it carries`() {
        val extras = Intent().putExtra("dev.cupr.calendar.tap.ELEMENT", "DAY").putExtra("a.stray.EXTRA", 1)
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertEquals(
            listOf(
                "An intent without a complete tap: element=DAY, day=null, " +
                    "extras: [a.stray.EXTRA, dev.cupr.calendar.tap.ELEMENT]",
            ),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `a malformed day opens nothing and is logged as given`() {
        val extras = tapExtras("DAY", "2026-13-45")
        assertNull(Taps.read(extras))
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertTrue(
            ShadowLog
                .getLogsForTag("TapActivity")
                .single()
                .msg
                .contains("element=DAY, day=2026-13-45"),
        )
    }

    @Test
    fun `an unknown element opens nothing and is logged as given`() {
        val extras = tapExtras("FOOTER", "2026-11-12")
        assertNull(Taps.read(extras))
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertTrue(
            ShadowLog
                .getLogsForTag("TapActivity")
                .single()
                .msg
                .contains("element=FOOTER, day=2026-11-12"),
        )
    }

    @Test
    fun `a target no app handles is logged and shown, not thrown`() {
        openTarget(application, "$calendar?day=2026-11-12") { throw ActivityNotFoundException("none") }
        assertEquals(
            listOf("No app opens $calendar?day=2026-11-12"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
        assertEquals("Home-Assistant-App nicht gefunden", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a Home Assistant target this app may not start says it does not open, not that the app is missing`() {
        openTarget(application, calendar) { throw SecurityException("denied") }
        assertEquals(listOf("Not allowed to open $calendar"), ShadowLog.getLogsForTag("TapActivity").map { it.msg })
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a target outside the Home Assistant app that does not open says so`() {
        openTarget(application, "other://x/2026-11-12") { throw ActivityNotFoundException("none") }
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
        openTarget(application, "other://x/2026-11-12") { throw SecurityException("denied") }
        assertEquals("Ziel lässt sich nicht öffnen", ShadowToast.getTextOfLatestToast())
        assertEquals(2, ShadowToast.shownToastCount())
    }

    private fun tapExtras(
        element: String,
        day: String,
    ) = Intent().putExtra("dev.cupr.calendar.tap.ELEMENT", element).putExtra("dev.cupr.calendar.tap.DAY", day)

    private fun launch(extras: Intent): TapActivity =
        Robolectric
            .buildActivity(TapActivity::class.java, Intent(extras).setClass(application, TapActivity::class.java))
            .create()
            .get()
}
