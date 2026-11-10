package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Parcel
import android.view.View
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The month widget's preview in the widget picker (`previewImage` in
 * widget_month_info.xml): the widget as a launcher draws it, outside night
 * mode, at its default size of 4 × 4 cells and xxhdpi, from the sample month.
 * The committed image is checked against a fresh render, so a change to the
 * drawing fails here until `mise run preview` has written it anew.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class MonthWidgetPreviewTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val output = File(System.getProperty("render.dir") ?: "build/render")

    @Test
    fun `renders the picker's preview to a PNG file`() {
        output.mkdirs()
        File(output, FILE).writeBytes(png(render()))
    }

    @Test
    fun `the committed preview is the widget as drawn now`() {
        // Both through a PNG, so the pixels a launcher decodes are compared, not the bitmap before encoding.
        val fresh = png(render()).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val committed = BitmapFactory.decodeResource(application.resources, R.drawable.widget_month_preview)
        assertTrue(
            "The widget's preview is out of date: run `mise run preview` and commit it",
            committed.sameAs(fresh),
        )
    }

    /** The widget applied in a widget host's view, laid out at its default size and drawn. */
    private fun render(): Bitmap {
        val density = application.resources.displayMetrics.density
        val width = (WIDTH_DP * density).toInt()
        val height = (HEIGHT_DP * density).toInt()
        val parcel = Parcel.obtain()
        val widget =
            try {
                MonthWidget.views(application, WIDTH_DP).writeToParcel(parcel, 0)
                parcel.setDataPosition(0)
                RemoteViews.CREATOR.createFromParcel(parcel).apply(application, AppWidgetHostView(application))
            } finally {
                parcel.recycle()
            }
        widget.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        widget.draw(Canvas(bitmap))
        return bitmap
    }

    private fun png(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream()
            .also { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
            .toByteArray()

    private companion object {
        /** The widget's default size, 4 × 4 cells (design.md). */
        const val WIDTH_DP = 276
        const val HEIGHT_DP = 456

        const val FILE = "widget_month_preview.png"
        const val PNG_QUALITY = 100
    }
}
