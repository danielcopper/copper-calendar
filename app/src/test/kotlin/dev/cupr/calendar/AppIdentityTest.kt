package dev.cupr.calendar

import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.widget.MonthWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the launcher and the widget picker show of the app: its name and the widget's name (D31). */
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
}
