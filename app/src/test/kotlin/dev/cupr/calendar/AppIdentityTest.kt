package dev.cupr.calendar

import android.app.Application
import android.content.ComponentName
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.widget.MonthWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the launcher and the widget picker show of the app: its name, the widget's name, its icon (D31, D32). */
@RunWith(RobolectricTestRunner::class)
class AppIdentityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the app is Copper Calendar and its widget Monat`() {
        val packageManager = application.packageManager
        assertEquals("Copper Calendar", application.applicationInfo.loadLabel(packageManager).toString())
        val widget = packageManager.getReceiverInfo(ComponentName(application, MonthWidgetProvider::class.java), 0)
        assertEquals("Monat", widget.loadLabel(packageManager).toString())
    }

    @Test
    fun `the launcher icon has a monochrome layer for themed icons`() {
        val icon = application.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
    }

    @Test
    fun `the launcher icon is a dark sheet on a light background outside night mode`() {
        assertEquals(0xFFD8E2F7.toInt(), application.getColor(R.color.ic_launcher_background))
        assertEquals(0xFF1E2B45.toInt(), application.getColor(R.color.ic_launcher_sheet))
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the launcher icon is a light sheet on a dark background in night mode`() {
        assertEquals(0xFF2B3245.toInt(), application.getColor(R.color.ic_launcher_background))
        assertEquals(0xFFD8E2FF.toInt(), application.getColor(R.color.ic_launcher_sheet))
    }
}
