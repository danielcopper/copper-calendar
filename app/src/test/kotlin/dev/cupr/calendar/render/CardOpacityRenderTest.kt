package dev.cupr.calendar.render

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.widget.MonthWidget
import dev.cupr.calendar.widget.WeekColumnsWidget
import dev.cupr.calendar.widget.WeekWidget
import dev.cupr.calendar.widget.WidgetHost
import dev.cupr.calendar.widget.views
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the three widgets of the sample data with their card at 100, 50
 * and 0 % (D64), light and dark, to PNG files under build/render, over a
 * striped backdrop standing in for a wallpaper, so what shows through the
 * card can be seen: at a Pixel 6's density, "Monat" and "Woche" 4 × 4 cells,
 * "Woche quer" 4 × 2 ([WidgetHost]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+420dpi")
class CardOpacityRenderTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `renders the widgets at 100, 50 and 0 percent, light, to PNG files`() {
        write("light")
    }

    @Test
    @Config(qualifiers = "+night")
    fun `renders the widgets at 100, 50 and 0 percent, dark, to PNG files`() {
        write("dark")
    }

    private fun write(mode: String) {
        val widgets =
            listOf<Triple<String, Int, (Int) -> (Context, Int) -> RemoteViews>>(
                Triple("monat", WidgetHost.HEIGHT_DP) { opacity ->
                    { context, width -> MonthWidget.views(context, width, opacity = opacity) }
                },
                Triple("woche", WidgetHost.HEIGHT_DP) { opacity ->
                    { context, width -> WeekWidget.views(context, width, opacity = opacity) }
                },
                Triple("woche-quer", WidgetHost.LOW_HEIGHT_DP) { opacity ->
                    { context, width -> WeekColumnsWidget.views(context, width, opacity = opacity) }
                },
            )
        Probe.renderDir.mkdirs()
        for ((name, heightDp, views) in widgets) {
            for (opacity in OPACITIES) {
                val widget = WidgetHost.laidOut(application, heightDp, views(opacity))
                val bitmap = Bitmap.createBitmap(widget.width, widget.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                backdrop(canvas, widget.width, widget.height)
                widget.draw(canvas)
                val file = File(Probe.renderDir, "widget-$name-opacity-$opacity-$mode.png")
                file.writeBytes(Probe.png(bitmap))
                assertTrue(file.isFile)
            }
        }
    }

    /** Diagonal stripes in two colours, a wallpaper's stand-in. */
    private fun backdrop(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        canvas.drawColor(STRIPE_A)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STRIPE_B }
        val stripe = Probe.scale.px(STRIPE_DP)
        var x = -height.toFloat()
        while (x < width) {
            val path =
                Path().apply {
                    moveTo(x, height.toFloat())
                    lineTo(x + stripe, height.toFloat())
                    lineTo(x + stripe + height, 0f)
                    lineTo(x + height, 0f)
                    close()
                }
            canvas.drawPath(path, paint)
            x += 2 * stripe
        }
    }

    private companion object {
        val OPACITIES = listOf(100, 50, 0)
        const val STRIPE_A = 0xFF3A6EA5.toInt()
        const val STRIPE_B = 0xFFE07A5F.toInt()
        const val STRIPE_DP = 24f
    }
}
