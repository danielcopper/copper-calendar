package dev.cupr.calendar.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.render.Probe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A widget's preview in the widget picker (`previewImage` in its provider's
 * info): the widget as a launcher draws it, outside night mode, at its
 * default size ([WidgetHost]) and xxhdpi, from the sample data (D33). The
 * committed image is checked against a fresh render, so a change to the
 * drawing, or to Robolectric's renderer, fails here until `mise run preview`
 * has written it anew. Each widget's test runs these three with its own.
 */
abstract class WidgetPreviewCheck(
    /** The file the fresh render is written to, as `mise run preview` copies it. */
    private val file: String,
    private val info: Int,
    private val preview: Int,
    private val heightDp: Int,
    private val views: (android.content.Context, Int) -> RemoteViews,
) {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val fresh = File(Probe.renderDir, file)

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
            checkNotNull(BitmapFactory.decodeResource(application.resources, preview)) {
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
        val parser = application.resources.getXml(info)
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) parser.next()
        assertEquals(preview, parser.getAttributeResourceValue(ANDROID, "previewImage", 0))
    }

    /** Draws the widget and writes it to [fresh]; returns its PNG bytes. */
    private fun write(): ByteArray {
        val widget = WidgetHost.laidOut(application, heightDp, views)
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
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
