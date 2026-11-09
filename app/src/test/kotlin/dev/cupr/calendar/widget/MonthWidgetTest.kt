package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Parcel
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.TapActivity
import dev.cupr.calendar.tap.TapElement
import dev.cupr.calendar.tap.Taps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(application)

    /** The widget as a launcher gets it: parcelled, then applied in a widget host's view. */
    private fun applied(widthDp: Int = 276): View {
        val parcel = Parcel.obtain()
        try {
            MonthWidget.views(application, widthDp).writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return RemoteViews.CREATOR.createFromParcel(parcel).apply(application, AppWidgetHostView(application))
        } finally {
            parcel.recycle()
        }
    }

    /** The widget laid out at 4 cells wide and heightDp high, so its list holds rows. */
    private fun laidOut(heightDp: Int = 456): View {
        val widget = applied()
        val density = application.resources.displayMetrics.density
        val width = View.MeasureSpec.makeMeasureSpec((276 * density).toInt(), View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY)
        widget.measure(width, height)
        widget.layout(0, 0, widget.measuredWidth, widget.measuredHeight)
        return widget
    }

    @Test
    fun `the list has one row per week of the month`() {
        val list = applied().findViewById<ListView>(R.id.list)
        assertEquals(6, list.adapter.count)
    }

    @Test
    fun `the header names the month`() {
        assertEquals("November 2026", applied().findViewById<View>(R.id.header).contentDescription)
    }

    @Test
    fun `a day's tap area names its day and sends that day to the tap activity`() {
        val list = laidOut().findViewById<ListView>(R.id.list)
        val row = list.getChildAt(2 - list.firstVisiblePosition)
        val wednesday = row.findViewById<View>(R.id.day_2)
        assertEquals("Mittwoch, 11. November", wednesday.contentDescription)
        wednesday.performClick()
        val started = shadowOf(application).nextStartedActivity
        assertEquals(TapActivity::class.java.name, started.component?.className)
        assertEquals(TapElement.DAY to LocalDate.of(2026, 11, 11), Taps.read(started))
    }

    @Test
    fun `the list opens scrolled to the current week`() {
        // Robolectric runs no smooth scroll; its shadow records where the list was asked to scroll.
        val list = applied().findViewById<ListView>(R.id.list)
        assertEquals(2, shadowOf(list).smoothScrolledPosition)
    }

    @Test
    fun `outside night mode the light drawings show`() {
        val widget = applied()
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.header_light).visibility)
        assertEquals(View.GONE, widget.findViewById<View>(R.id.header_dark).visibility)
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.fab_light).visibility)
        assertEquals(View.GONE, widget.findViewById<View>(R.id.fab_dark).visibility)
        val list = widget.findViewById<ListView>(R.id.list)
        val row = list.adapter.getView(0, null, list)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.week_light).visibility)
        assertEquals(View.GONE, row.findViewById<View>(R.id.week_dark).visibility)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the dark drawings show`() {
        val widget = applied()
        assertEquals(View.GONE, widget.findViewById<View>(R.id.header_light).visibility)
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.header_dark).visibility)
        assertEquals(View.GONE, widget.findViewById<View>(R.id.fab_light).visibility)
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.fab_dark).visibility)
        val list = widget.findViewById<ListView>(R.id.list)
        val row = list.adapter.getView(0, null, list)
        assertEquals(View.GONE, row.findViewById<View>(R.id.week_light).visibility)
        assertEquals(View.VISIBLE, row.findViewById<View>(R.id.week_dark).visibility)
    }

    @Test
    fun `the card's colours are the light palette's outside night mode`() {
        assertEquals(SampleData.light.card, application.getColor(R.color.widget_card))
        assertEquals(SampleData.light.cardBorder, application.getColor(R.color.widget_card_border))
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the card's colours are the dark palette's in night mode`() {
        assertEquals(SampleData.dark.card, application.getColor(R.color.widget_card))
        assertEquals(SampleData.dark.cardBorder, application.getColor(R.color.widget_card_border))
    }

    @Test
    fun `an update draws the widget`() {
        val id = shadowOf(manager).createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        val widget = shadowOf(manager).getViewFor(id)
        for (image in listOf(R.id.header_light, R.id.header_dark, R.id.fab_light, R.id.fab_dark)) {
            assertTrue(widget.findViewById<ImageView>(image).drawable is BitmapDrawable)
        }
    }

    @Test
    fun `a size change draws the rows at the new width`() {
        val id = shadowOf(manager).createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) }
        manager.updateAppWidgetOptions(id, options)
        MonthWidgetProvider().onAppWidgetOptionsChanged(application, manager, id, options)
        val header = shadowOf(manager).getViewFor(id).findViewById<ImageView>(R.id.header_light)
        val density = application.resources.displayMetrics.density
        assertEquals(((180 - 26) * density).toInt(), (header.drawable as BitmapDrawable).bitmap.width)
    }
}
