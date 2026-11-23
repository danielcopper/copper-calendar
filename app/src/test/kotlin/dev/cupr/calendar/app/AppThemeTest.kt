package dev.cupr.calendar.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import dev.cupr.design.CopperColorScheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** The app's screens take copper's colour scheme, light or dark with the system (D65). */
@RunWith(RobolectricTestRunner::class)
class AppThemeTest {
    private val opened = mutableListOf<ActivityController<ComponentActivity>>()

    @After
    fun tearDown() {
        for (controller in opened) controller.pause().stop().destroy()
    }

    @Test
    fun `in the light, the screens have copper's light colour scheme`() {
        assertNotEquals(CopperColorScheme.dark.toString(), CopperColorScheme.light.toString())
        assertEquals(CopperColorScheme.light.toString(), scheme().toString())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in the dark, the screens have copper's dark colour scheme`() {
        assertEquals(CopperColorScheme.dark.toString(), scheme().toString())
    }

    /** The colour scheme a composable inside [AppTheme] reads. */
    private fun scheme(): ColorScheme {
        var scheme: ColorScheme? = null
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        opened += controller
        controller.get().setContent { AppTheme { scheme = MaterialTheme.colorScheme } }
        shadowOf(Looper.getMainLooper()).idle()
        return checkNotNull(scheme)
    }
}
