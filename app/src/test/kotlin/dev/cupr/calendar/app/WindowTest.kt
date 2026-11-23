package dev.cupr.calendar.app

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.children
import androidx.core.view.insets.ColorProtection
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.setup.SetupActivity
import dev.cupr.calendar.signin.SignInActivity
import dev.cupr.design.CopperColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Until Compose draws, the window of the sign-in screen and of the widgets' setup, and the splash screen, show the
 * background of copper's colour scheme, light or dark with the system, and so do the system bars' scrims (D65). The
 * setup's bars: SetupScreenTest.
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

    @Test
    fun `the sign-in screen's system bars are copper's light background`() {
        assertSignInBars(CopperColorScheme.light.background.toArgb())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the sign-in screen's system bars are copper's dark background`() {
        assertSignInBars(CopperColorScheme.dark.background.toArgb())
    }

    private fun assertSignInBars(background: Int) {
        val activity = Robolectric.buildActivity(SignInActivity::class.java).setup()
        try {
            assertBars(background, activity.get())
        } finally {
            activity.pause().stop().destroy()
        }
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

/**
 * Asserts that the system bars' scrims of [activity] are [background]. From Android 15 on, edge-to-edge draws them
 * itself, as one ColorProtection per side, held in the tag of a layout it adds to the window's decor view.
 */
internal fun assertBars(
    background: Int,
    activity: Activity,
) {
    val api = Build.VERSION.SDK_INT
    assertTrue("Robolectric runs API $api", api >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
    val scrims =
        (activity.window.decorView as ViewGroup)
            .children
            .mapNotNull { it.tag as? List<*> }
            .flatten()
            .filterIsInstance<ColorProtection>()
            .toList()
    assertEquals("one scrim per side", SIDES, scrims.size)
    for (scrim in scrims) {
        assertEquals("side ${scrim.side}", Integer.toHexString(background), Integer.toHexString(scrim.color))
    }
}

/** The four sides of the window edge-to-edge protects. */
private const val SIDES = 4
