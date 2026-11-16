package dev.cupr.calendar.store

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FailedFetchStoreTest {
    private val prefs =
        ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("account", Context.MODE_PRIVATE)
    private val store = FailedFetchStore(prefs)

    @Test
    fun `every cause reads back as it was written, under its own fixed name`() {
        for (cause in FetchCause.entries) {
            store.write(FailedFetch(Instant.ofEpochMilli(1_000), cause))

            assertEquals(FailedFetch(Instant.ofEpochMilli(1_000), cause), store.read())
            assertEquals(cause.stored, prefs.getString("failed_cause", null))
        }
        assertEquals(
            listOf("unreachable", "no_network", "timeout", "sensor_missing", "refused", "unexpected", "phone"),
            FetchCause.entries.map { it.stored },
        )
    }

    @Test
    fun `a cause this app does not know reads as unexpected, and is logged`() {
        prefs.edit {
            putLong("failed_at", 1_000)
            putString("failed_cause", "UNEXPECTED")
        }

        assertEquals(FailedFetch(Instant.ofEpochMilli(1_000), FetchCause.UNEXPECTED), store.read())
        val logs = ShadowLog.getLogsForTag("FailedFetch").joinToString { it.msg }
        assertTrue(logs, logs.contains("cause \"UNEXPECTED\" is unknown"))
    }

    @Test
    fun `cleared, there is no failed fetch, nor an earlier build's detail`() {
        store.write(FailedFetch(Instant.ofEpochMilli(1_000), FetchCause.PHONE))
        prefs.edit { putString("failed_detail", "refresh: HTTP 502") }

        store.clear()

        assertNull(store.read())
        assertEquals(emptySet<String>(), prefs.all.keys)
    }
}
