package dev.cupr.calendar.render

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.widget.DrawnWidget
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * Renders the plain views a widget shows instead of its drawing (D40), too
 * full or failed, light and dark, to PNG files under build/render, at a
 * Pixel 6's density and 4 × 2 cells (276 × 220 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+420dpi")
class PlainViewsRenderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `renders the too-full and failed views, light, to PNG files`() {
        write("light")
    }

    @Test
    @Config(qualifiers = "+night")
    fun `renders the too-full and failed views, dark, to PNG files`() {
        write("dark")
    }

    private fun write(mode: String) {
        val scale = Scale(application.resources.displayMetrics.density)
        val texts =
            listOf(
                "too-full-week" to R.string.widget_week_too_full,
                "too-full-month" to R.string.widget_month_too_full,
                "failed" to R.string.widget_failed,
            )
        Probe.renderDir.mkdirs()
        for ((name, text) in texts) {
            val view = DrawnWidget.plainViews(application, text).apply(application, AppWidgetHostView(application))
            val spec = { dp: Int ->
                View.MeasureSpec.makeMeasureSpec(scale.px(dp.toFloat()).roundToInt(), View.MeasureSpec.EXACTLY)
            }
            view.measure(spec(WIDTH_DP), spec(HEIGHT_DP))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File(Probe.renderDir, "widget-$name-$mode.png")
            file.writeBytes(Probe.png(bitmap))
            assertTrue(file.isFile)
        }
    }

    private companion object {
        const val WIDTH_DP = 276
        const val HEIGHT_DP = 220
    }
}
