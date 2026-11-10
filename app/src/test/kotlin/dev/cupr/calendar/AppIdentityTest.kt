package dev.cupr.calendar

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.render.Probe
import dev.cupr.calendar.widget.MonthWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * What the launcher and the widget picker show of the app: its name, the
 * widget's name, its icon (D31, D32). The icon's layers are drawn at one pixel
 * per unit of the adaptive icon's 108-unit viewport and probed inside its
 * shapes: the sheet above the days, a ring above the sheet, a day and today.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIdentityTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val icon get() = application.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

    @Test
    fun `the app is Copper Calendar and its widget Monat`() {
        val packageManager = application.packageManager
        assertEquals("Copper Calendar", application.applicationInfo.loadLabel(packageManager).toString())
        val widget = packageManager.getReceiverInfo(ComponentName(application, MonthWidgetProvider::class.java), 0)
        assertEquals("Monat", widget.loadLabel(packageManager).toString())
    }

    @Test
    fun `the themed icon is the sheet and its rings with the days cut out`() {
        val mono = drawn(checkNotNull(icon.monochrome) { "The launcher icon has no monochrome layer" })
        assertEquals("sheet", OPAQUE, Color.alpha(mono.getPixel(SHEET_X, SHEET_Y)))
        assertEquals("ring", OPAQUE, Color.alpha(mono.getPixel(RING_X, RING_Y)))
        assertEquals("a day", 0, Color.alpha(mono.getPixel(DAY_X, DAY_Y)))
        assertEquals("today", 0, Color.alpha(mono.getPixel(TODAY_X, TODAY_Y)))
    }

    @Test
    fun `the launcher icon is a dark sheet on a light background outside night mode`() {
        assertIcon(background = 0xFFD8E2F7.toInt(), sheet = 0xFF1E2B45.toInt())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the launcher icon is a light sheet on a dark background in night mode`() {
        assertIcon(background = 0xFF2B3245.toInt(), sheet = 0xFFD8E2FF.toInt())
    }

    /**
     * The background's colour, and the foreground's sheet, ring, a day (the
     * background at 45 % on the sheet) and today.
     */
    private fun assertIcon(
        background: Int,
        sheet: Int,
    ) {
        val icon = icon
        assertEquals("background", background, drawn(icon.background).getPixel(SHEET_X, SHEET_Y))
        val front = drawn(icon.foreground)
        assertEquals("sheet", sheet, front.getPixel(SHEET_X, SHEET_Y))
        assertEquals("ring", sheet, front.getPixel(RING_X, RING_Y))
        val day = front.getPixel(DAY_X, DAY_Y)
        assertTrue("a day is ${Integer.toHexString(day)}", Probe.near(day, blend(background, sheet)))
        assertEquals("today", background, front.getPixel(TODAY_X, TODAY_Y))
    }

    private fun drawn(layer: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(VIEWPORT, VIEWPORT, Bitmap.Config.ARGB_8888)
        layer.setBounds(0, 0, VIEWPORT, VIEWPORT)
        layer.draw(Canvas(bitmap))
        return bitmap
    }

    /** The day's colour: the background at 45 % opacity over the sheet. */
    private fun blend(
        background: Int,
        sheet: Int,
    ): Int {
        fun channel(of: (Int) -> Int) = (of(background) * DAY_OPACITY + of(sheet) * (1 - DAY_OPACITY)).roundToInt()
        return Color.rgb(channel(Color::red), channel(Color::green), channel(Color::blue))
    }

    private companion object {
        const val VIEWPORT = 108
        const val OPAQUE = 255
        const val DAY_OPACITY = 0.45f

        // Pixels inside the shapes of the icon's drawables.
        const val SHEET_X = 55
        const val SHEET_Y = 47
        const val RING_X = 44
        const val RING_Y = 34
        const val DAY_X = 41
        const val DAY_Y = 54
        const val TODAY_X = 59
        const val TODAY_Y = 62
    }
}
