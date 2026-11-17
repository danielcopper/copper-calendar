package dev.cupr.calendar.store

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class AppSettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val store = AppSettingsStore(prefs)

    @Test
    fun `the app fetches every 15 minutes until another interval is set`() {
        assertEquals(FetchInterval.EVERY_15_MINUTES, store.fetchInterval())
    }

    @Test
    fun `each fetch interval reads back as it was written, under its own fixed name`() {
        for (interval in FetchInterval.entries) {
            store.writeFetchInterval(interval)
            assertEquals(interval, store.fetchInterval())
            assertEquals(interval.stored, prefs.getString("fetch_interval", null))
        }
        assertEquals(listOf("15", "30", "60"), FetchInterval.entries.map { it.stored })
        assertEquals(listOf(15L, 30L, 60L), FetchInterval.entries.map { it.minutes })
    }

    @Test
    fun `an interval this app does not know reads as 15 minutes, and is logged`() {
        prefs.edit { putString("fetch_interval", "5") }

        assertEquals(FetchInterval.EVERY_15_MINUTES, store.fetchInterval())
        val logs = ShadowLog.getLogsForTag("AppSettings").joinToString { it.msg }
        assertTrue(logs, logs.contains("The stored fetch interval \"5\" is unknown; it reads as 15"))
    }
}
