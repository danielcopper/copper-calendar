package dev.cupr.calendar.app

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.core.view.insets.ColorProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Asserts that the system bars' scrims of [activity] are [background], and that their icons are drawn for a light
 * background exactly when [light]. From Android 15 on, edge-to-edge draws the scrims itself, as one ColorProtection
 * per side, held in the tag of a layout it adds to the window's decor view.
 */
internal fun assertBars(
    background: Int,
    light: Boolean,
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
    val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    assertEquals("status bar icons for a light background", light, bars.isAppearanceLightStatusBars)
    assertEquals("navigation bar icons for a light background", light, bars.isAppearanceLightNavigationBars)
}

/** The four sides of the window edge-to-edge protects. */
private const val SIDES = 4
