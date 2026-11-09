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
        val header = shadowOf(Taps.pendingIntent(application, TapElement.HEADER, day)).savedIntent
        assertEquals(TapActivity::class.java.name, header.component?.className)
        assertEquals(TapElement.HEADER to day, Taps.read(header))
        val plus = shadowOf(Taps.pendingIntent(application, TapElement.NEW, day)).savedIntent
        assertEquals(TapElement.NEW to day, Taps.read(plus))
    }

    @Test
    fun `nothing crashes when no app handles the target`() {
        shadowOf(application).checkActivities(true)
        val activity = launch(Taps.fillIn(TapElement.NEW, day))
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `an intent without a complete tap opens nothing`() {
        val activity = launch(Intent().putExtra("dev.cupr.calendar.tap.ELEMENT", "DAY"))
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    private fun launch(extras: Intent): TapActivity =
        Robolectric
            .buildActivity(TapActivity::class.java, Intent(extras).setClass(application, TapActivity::class.java))
            .create()
            .get()
}
