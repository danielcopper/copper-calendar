package dev.cupr.calendar.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.render.Probe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * The month widget's preview in the widget picker (`previewImage` in
 * widget_month_info.xml): the widget as a launcher draws it, outside night
 * mode, at its default size ([WidgetHost]) and xxhdpi, from the sample month.
 * The committed image is checked against a fresh render, so a change to the
 * drawing, or to Robolectric's renderer, fails here until `mise run preview`
 * has written it anew.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class MonthWidgetPreviewTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val fresh = File(Probe.renderDir, FILE)

    @Test
    fun `renders the picker's preview to a PNG file`() {
        write()
    }

    @Test
    fun `the committed preview is the widget as drawn now`() {
        // Both through a PNG, so the pixels a launcher decodes are compared, not the bitmap before encoding.
        val bytes = write()
        val drawn =
            checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "The fresh preview does not decode" }
        val committed =
            checkNotNull(BitmapFactory.decodeResource(application.resources, R.drawable.widget_month_preview)) {
                "The committed preview does not decode"
            }
        assertTrue(
            "The widget's preview is out of date: committed ${committed.width} x ${committed.height}, " +
                "fresh ${drawn.width} x ${drawn.height}, ${differing(committed, drawn)} pixels differ; " +
                "the fresh one is $fresh. Run `mise run preview` and commit it.",
            committed.sameAs(drawn),
        )
    }

    @Test
    fun `the widget picker shows the committed preview`() {
        val parser = application.resources.getXml(R.xml.widget_month_info)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()
        assertEquals(
            R.drawable.widget_month_preview,
            parser.getAttributeResourceValue(ANDROID, "previewImage", 0),
        )
    }

    /** Draws the widget and writes it to [fresh]; returns its PNG bytes. */
    private fun write(): ByteArray {
        val widget = WidgetHost.laidOut(application)
        val bitmap = Bitmap.createBitmap(widget.width, widget.height, Bitmap.Config.ARGB_8888)
        widget.draw(Canvas(bitmap))
        val bytes = Probe.png(bitmap)
        Probe.renderDir.mkdirs()
        fresh.writeBytes(bytes)
        return bytes
    }

    /** How many pixels differ, or "all" when the sizes do. */
    private fun differing(
        a: Bitmap,
        b: Bitmap,
    ): String {
        if (a.width != b.width || a.height != b.height) return "all"
        val left = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val right = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return left.indices.count { left[it] != right[it] }.toString()
    }

    private companion object {
        const val FILE = "widget_month_preview.png"
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
