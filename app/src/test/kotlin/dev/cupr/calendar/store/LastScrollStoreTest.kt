package dev.cupr.calendar.store

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class LastScrollStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scrolls = LastScrollStore(context.getSharedPreferences("widgets", Context.MODE_PRIVATE))

    @Test
    fun `a widget's last scroll reads back for it alone, and goes with the widget`() {
        val at = Instant.ofEpochMilli(1_000)

        scrolls.write(7, at)
        scrolls.write(8, at)

        assertEquals(at, scrolls.read(7))
        assertNull(scrolls.read(9))
        scrolls.forget(intArrayOf(7))
        assertNull(scrolls.read(7))
        assertEquals(at, scrolls.read(8))
    }
}
