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
class LastScrollStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val scrolls = LastScrollStore(prefs)

    @Test
    fun `a widget's last scroll reads back for it alone, and goes with the widget`() {
        val at = Instant.ofEpochMilli(1_000)

        scrolls.keep(7, ListState.SETTLED, at)
        scrolls.keep(8, ListState.SETTLED, at)

        assertEquals(at, scrolls.read(7))
        assertNull(scrolls.read(9))
        scrolls.forget(intArrayOf(7))
        assertNull(scrolls.read(7))
        assertEquals(at, scrolls.read(8))
    }

    @Test
    fun `a list is built anew until a state is kept, which reads back under its own fixed name`() {
        assertEquals(ListState.BUILT_ANEW, scrolls.state(7))
        for (state in ListState.entries) {
            scrolls.keep(7, state, null)
            assertEquals(state, scrolls.state(7))
            assertEquals(state.stored, prefs.getString("7.list", null))
        }
        assertEquals(listOf("built_anew", "first_scroll", "settled"), ListState.entries.map { it.stored })
    }

    @Test
    fun `keeping a state without a scroll keeps the last scroll, forgetting the list takes both`() {
        val at = Instant.ofEpochMilli(1_000)
        scrolls.keep(7, ListState.SETTLED, at)

        scrolls.keep(7, ListState.SETTLED, null)
        assertEquals(at, scrolls.read(7))
        scrolls.forget(intArrayOf(7))

        assertNull(scrolls.read(7))
        assertEquals(ListState.BUILT_ANEW, scrolls.state(7))
        assertTrue(prefs.all.keys.none { it.startsWith("7.") })
    }

    @Test
    fun `a state this app does not know reads as built anew, and is logged`() {
        prefs.edit { putString("7.list", "scrolled") }

        assertEquals(ListState.BUILT_ANEW, scrolls.state(7))
        val logs = ShadowLog.getLogsForTag("LastScroll").joinToString { it.msg }
        assertTrue(logs, logs.contains("Widget 7: its list's stored state \"scrolled\" is unknown; it reads as built_anew"))
    }
}
