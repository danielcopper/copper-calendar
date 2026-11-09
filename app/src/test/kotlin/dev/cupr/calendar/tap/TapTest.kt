package dev.cupr.calendar.tap

import android.app.Application
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TapTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val day = LocalDate.of(2026, 11, 12)

    @Test
    fun `the header opens the calendar page`() {
        assertEquals(
            "homeassistant://navigate/dashboard-zuhause/kalender",
            TapTargets.uri(TapElement.HEADER, day),
        )
    }

    @Test
    fun `a day opens the calendar page on that day`() {
        assertEquals(
            "homeassistant://navigate/dashboard-zuhause/kalender?day=2026-11-12",
            TapTargets.uri(TapElement.DAY, day),
        )
    }

    @Test
    fun `plus opens the form for a new entry on the day it is given`() {
        assertEquals(
            "homeassistant://navigate/dashboard-zuhause/kalender?new=2026-11-12",
            TapTargets.uri(TapElement.NEW, day),
        )
    }

    @Test
    fun `the tap activity opens the tapped day's target and finishes`() {
        val activity = launch(Taps.fillIn(TapElement.DAY, day))
        val opened = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("homeassistant://navigate/dashboard-zuhause/kalender?day=2026-11-12", opened.dataString)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `the fixed parts' pending intents carry their element and day to the tap activity`() {
        // Both made before either is read, so one cannot overwrite the other.
        val header = Taps.pendingIntent(application, TapElement.HEADER, day)
        val plus = Taps.pendingIntent(application, TapElement.NEW, day)
        val headerIntent = shadowOf(header).savedIntent
        assertEquals(TapActivity::class.java.name, headerIntent.component?.className)
        assertEquals(TapElement.HEADER to day, Taps.read(headerIntent))
        assertEquals(TapElement.NEW to day, Taps.read(shadowOf(plus).savedIntent))
    }

    @Test
    fun `nothing crashes when no app handles the target`() {
        shadowOf(application).checkActivities(true)
        val activity = launch(Taps.fillIn(TapElement.NEW, day))
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `an intent without a complete tap opens nothing and is logged with its extras' keys`() {
        val activity = launch(Intent().putExtra("dev.cupr.calendar.tap.ELEMENT", "DAY"))
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
        assertEquals(
            listOf("A tap without a complete target, extras: [dev.cupr.calendar.tap.ELEMENT]"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    @Test
    fun `a malformed day opens nothing`() {
        val extras =
            Intent()
                .putExtra(
                    "dev.cupr.calendar.tap.ELEMENT",
                    "DAY",
                ).putExtra("dev.cupr.calendar.tap.DAY", "2026-13-45")
        assertNull(Taps.read(extras))
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `an unknown element opens nothing`() {
        val extras =
            Intent()
                .putExtra(
                    "dev.cupr.calendar.tap.ELEMENT",
                    "FOOTER",
                ).putExtra("dev.cupr.calendar.tap.DAY", "2026-11-12")
        assertNull(Taps.read(extras))
        val activity = launch(extras)
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a target the app may not open is logged, not thrown`() {
        openTarget("homeassistant://navigate/dashboard-zuhause/kalender") { throw SecurityException("denied") }
        assertEquals(
            listOf("Not allowed to open homeassistant://navigate/dashboard-zuhause/kalender"),
            ShadowLog.getLogsForTag("TapActivity").map { it.msg },
        )
    }

    private fun launch(extras: Intent): TapActivity =
        Robolectric
            .buildActivity(TapActivity::class.java, Intent(extras).setClass(application, TapActivity::class.java))
            .create()
            .get()
}
