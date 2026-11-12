package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.TapActivity
import dev.cupr.calendar.tap.Taps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.LocalDate
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonthWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(application)

    @Test
    fun `the list has one row per week of the month`() {
        val list = WidgetHost.applied(application).findViewById<ListView>(R.id.list)
        assertEquals(6, list.adapter.count)
    }

    @Test
    fun `the header draws the weekday heads from the string resource`() {
        val header = WidgetHost.applied(application).findViewById<ImageView>(R.id.header_light)
        val assets = RenderAssets.of(application)
        val weekdays = application.resources.getStringArray(R.array.weekday_heads).toList()
        val scale = Scale(application.resources.displayMetrics.density)
        val widthPx = scale.px(WidgetHost.WIDTH_DP - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val expected =
            ChromeRenderer(assets.fonts, assets.icons, weekdays)
                .header(SampleData.month, widthPx, scale, SampleData.light)
        assertTrue((header.drawable as BitmapDrawable).bitmap.sameAs(expected))
    }

    @Test
    fun `the header names the month`() {
        val header = WidgetHost.applied(application).findViewById<View>(R.id.header)
        assertEquals("November 2026", header.contentDescription)
    }

    @Test
    fun `a day's tap area names its day and sends that day to the tap activity`() {
        val list = WidgetHost.laidOut(application).findViewById<ListView>(R.id.list)
        val row = list.getChildAt(2 - list.firstVisiblePosition)
        val wednesday = row.findViewById<View>(R.id.day_2)
        assertEquals("Mittwoch, 11. November", wednesday.contentDescription)
        wednesday.performClick()
        val started = shadowOf(application).nextStartedActivity
        assertEquals(TapActivity::class.java.name, started.component?.className)
        assertEquals(Tap.Day(LocalDate.of(2026, 11, 11)), Taps.read(started))
    }

    /** The month's list for a widget heightDp high as the launcher reports it, scrolled to its target, and that. */
    private fun scrolled(heightDp: Int): Pair<ListView, Int> {
        val height = WidgetHeight(heightDp, null)
        return WidgetHost.scrolledToTarget(application, heightDp, { context, width ->
            MonthWidget.draw(context, width, 1, Long.MAX_VALUE, height).views
        })
    }

    @Test
    fun `scrolled to its target, the current week stands at the top, the weeks after it below`() {
        // From 2 cells (220 dp) to 320 dp the weeks from the current one on fill more than the list.
        for ((heightDp, expected) in listOf(220 to 2, 260 to 3, 300 to 3, 320 to 4)) {
            val (list, target) = scrolled(heightDp)
            assertEquals("$heightDp dp", expected, target)
            val week = { index: Int -> list.getChildAt(index - list.firstVisiblePosition) }
            assertEquals("$heightDp dp: the current week's top", 0, week(2).top)
            // The list does not clip its rows to its padding, so the week after the target shows in it.
            assertNotNull("$heightDp dp: week ${target + 1} is not shown", week(target + 1))
        }
        // At 4 cells the weeks from the current one fill less than the list, so it scrolls to its end, unpadded.
        val (list, target) = scrolled(WidgetHost.HEIGHT_DP)
        assertEquals(5, target)
        assertEquals(0, list.paddingBottom)
        assertEquals(list.height, list.getChildAt(5 - list.firstVisiblePosition).bottom)
        val log = ShadowLog.getLogsForTag("MonthWidget").last { it.type == Log.INFO }.msg
        assertTrue(log, log.contains("; today is row 2, scrolls to row 5, 4 rows, "))
        assertTrue(log, log.contains(" px, no padding: the rows to the end fit; "))
        assertTrue(log, log.endsWith("; 456 dp (appWidgetMaxHeight, no least height)"))
    }

    @Test
    @Config(qualifiers = "+xxhdpi")
    fun `at xxhdpi too, the current week stands at the top, the weeks after it below`() {
        val (list, target) = scrolled(300)
        assertTrue("$target", target in 3 until 5)
        assertEquals(0, list.getChildAt(2 - list.firstVisiblePosition).top)
        assertNotNull(list.getChildAt(target + 1 - list.firstVisiblePosition))
    }

    @Test
    fun `an update to a height where the weeks from the current one fit clears the padding before`() {
        val (list, _) = scrolled(220)
        assertTrue("${list.paddingBottom}", list.paddingBottom > 0)
        val height = WidgetHeight(WidgetHost.HEIGHT_DP, null)
        WidgetHost.reapplied(application, list.rootView) { context, width ->
            MonthWidget.draw(context, width, 1, Long.MAX_VALUE, height).views
        }
        assertEquals(5, WidgetHost.scrollToTarget(list))
        assertEquals(0, list.paddingBottom)
        assertEquals(list.height, list.getChildAt(5 - list.firstVisiblePosition).bottom)
    }

    @Test
    fun `an update scrolls for the widget's greatest height, its height in portrait`() {
        val id = shadowOf(manager).createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        val options =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 320)
            }
        manager.updateAppWidgetOptions(id, options)
        ShadowLog.clear()
        MonthWidgetProvider().onAppWidgetOptionsChanged(application, manager, id, options)
        val log = ShadowLog.getLogsForTag("MonthWidget").single { it.type == Log.INFO }.msg
        assertTrue(log, log.contains("; today is row 2, scrolls to row 4, 3 rows, 214 of the list's 241 px, "))
        assertTrue(log, log.contains(", bottom padding 27 px, at least the 12 px the scroll stops above the bottom; "))
        assertTrue(log, log.endsWith("; 320 dp (appWidgetMaxHeight, 200 dp least)"))
    }

    @Test
    fun `without a reported height an update scrolls the current week only into view, unpadded`() {
        val id = shadowOf(manager).createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        val list = shadowOf(manager).getViewFor(id).findViewById<ListView>(R.id.list)
        assertEquals(2, shadowOf(list).smoothScrolledPosition)
        assertEquals(0, list.paddingBottom)
        val log = ShadowLog.getLogsForTag("MonthWidget").single { it.type == Log.INFO }.msg
        assertTrue(log, log.endsWith("; today is row 2, scrolls it into view: no height"))
        val warnings = ShadowLog.getLogsForTag("MonthWidget").filter { it.type == Log.WARN }.map { it.msg }
        val expected =
            "Widget $id: the launcher reports no appWidgetMaxHeight, so its list scrolls today only into view"
        assertTrue(warnings.toString(), expected in warnings)
    }

    @Test
    fun `the header and plus send their tap to the tap activity, plus with today`() {
        val widget = WidgetHost.applied(application)
        widget.findViewById<View>(R.id.header).performClick()
        val header = shadowOf(application).nextStartedActivity
        assertNotNull("the header started nothing", header)
        assertEquals(Tap.Header, Taps.read(header))
        widget.findViewById<View>(R.id.fab).performClick()
        val plus = shadowOf(application).nextStartedActivity
        assertNotNull("plus started nothing", plus)
        assertEquals(Tap.New(LocalDate.of(2026, 11, 11)), Taps.read(plus))
    }

    @Test
    fun `outside night mode the light drawings show`() {
        val widget = WidgetHost.applied(application)
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
        val widget = WidgetHost.applied(application)
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
        val log = ShadowLog.getLogsForTag("MonthWidget").single { it.type == Log.INFO }.msg
        assertTrue(log, log.startsWith("Widget $id: drew 16 bitmaps at "))
    }

    @Test
    fun `only the system may send the widget's broadcasts`() {
        val receiver =
            application.packageManager.getReceiverInfo(ComponentName(application, MonthWidgetProvider::class.java), 0)
        assertTrue(receiver.exported)
        assertEquals("android.permission.BIND_APPWIDGET", receiver.permission)
    }

    @Test
    fun `the app's data is not backed up`() {
        assertEquals(0, application.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `the card's border is the drawn parts' border`() {
        val density = application.resources.displayMetrics.density
        assertEquals(PhoneMonth.CARD_BORDER * density, application.resources.getDimension(R.dimen.widget_card_border))
    }

    @Test
    fun `a size change draws the rows at the new width`() {
        val id = shadowOf(manager).createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) }
        manager.updateAppWidgetOptions(id, options)
        MonthWidgetProvider().onAppWidgetOptionsChanged(application, manager, id, options)
        val header = shadowOf(manager).getViewFor(id).findViewById<ImageView>(R.id.header_light)
        val scale = Scale(application.resources.displayMetrics.density)
        val widthPx = scale.px(180 - 2 * PhoneMonth.CARD_INSET).roundToInt()
        assertEquals(widthPx, (header.drawable as BitmapDrawable).bitmap.width)
        // The rows at that width: Robolectric's widget host shows no list, so they are read from the views for it.
        val list = WidgetHost.applied(application, 180).findViewById<ListView>(R.id.list)
        val row = list.adapter.getView(0, null, list)
        assertEquals(widthPx, (row.findViewById<ImageView>(R.id.week_light).drawable as BitmapDrawable).bitmap.width)
    }

    @Test
    @Config(qualifiers = "+xxhdpi")
    fun `the layout's measures fit the drawn parts`() {
        val scale = Scale(application.resources.displayMetrics.density)
        val px = { dp: Float -> scale.px(dp).roundToInt() }
        val widget = WidgetHost.laidOut(application)
        val list = widget.findViewById<ListView>(R.id.list)
        val header = widget.findViewById<View>(R.id.header)
        val side = px(PhoneMonth.CARD_INSET)
        assertEquals(side, header.left + (header.parent as View).left)
        assertEquals(px(PhoneMonth.CARD_BORDER + PhoneMonth.CARD_END), header.top + (header.parent as View).top)
        val week = list.getChildAt(0).findViewById<ImageView>(R.id.week_light)
        val bitmap = (week.drawable as BitmapDrawable).bitmap
        // Drawn at the list's width and the display's density, so the row is shown unscaled.
        assertEquals(list.width, bitmap.width)
        assertEquals(week.width, bitmap.width)
        assertEquals(week.height, bitmap.height)
        val fabImage = widget.findViewById<ImageView>(R.id.fab_light)
        val fabBitmap = (fabImage.drawable as BitmapDrawable).bitmap
        assertEquals(fabImage.width, fabBitmap.width)
        assertEquals(widget.width, fabImage.right)
        val fab = widget.findViewById<View>(R.id.fab)
        assertEquals(px(PhoneMonth.FAB_SIZE), fab.width)
        assertEquals(px(PhoneMonth.FAB_MARGIN), widget.width - fab.right)
        assertEquals(px(PhoneMonth.FAB_MARGIN), widget.height - fab.bottom)
    }
}
