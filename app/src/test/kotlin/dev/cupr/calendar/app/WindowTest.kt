package dev.cupr.calendar.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.view.ContextThemeWrapper
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.setup.SetupActivity
import dev.cupr.calendar.signin.SignInActivity
import dev.cupr.design.CopperColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Until Compose draws, the window of the sign-in screen and of the widgets' setup, and the splash screen, show the
 * background of copper's colour scheme, light or dark with the system (D65).
 */
@RunWith(RobolectricTestRunner::class)
class WindowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the screens' window and splash screen are copper's light background`() {
        assertWindow(CopperColorScheme.light.background.toArgb())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the screens' window and splash screen are copper's dark background`() {
        assertWindow(CopperColorScheme.dark.background.toArgb())
    }

    private fun assertWindow(background: Int) {
        for (activity in listOf(SignInActivity::class.java, SetupActivity::class.java)) {
            assertEquals(activity.simpleName, R.style.Theme_CopperCalendar, themeOf(activity))
        }
        val attributes =
            ContextThemeWrapper(context, R.style.Theme_CopperCalendar).obtainStyledAttributes(
                intArrayOf(android.R.attr.windowBackground, android.R.attr.windowSplashScreenBackground),
            )
        attributes.use {
            assertEquals("window", Integer.toHexString(background), Integer.toHexString(it.getColor(0, 0)))
            assertEquals("splash screen", Integer.toHexString(background), Integer.toHexString(it.getColor(1, 0)))
        }
    }

    private fun themeOf(activity: Class<out Activity>): Int =
        context.packageManager.getActivityInfo(ComponentName(context, activity), 0).theme
}
