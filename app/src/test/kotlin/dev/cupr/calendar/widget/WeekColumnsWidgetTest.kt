package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekRowRenderer
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.TapActivity
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.WeekDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekColumnsWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(application)
    private val wednesday = LocalDate.of(2026, 11, 11)

    private fun columns(heightDp: Int = WidgetHost.LOW_HEIGHT_DP): (Context, Int) -> RemoteViews =
        { context, width -> WeekColumnsWidget.views(context, width, heightDp) }

    private val fromToday: (Context, Int) -> RemoteViews =
        { context, width -> WeekColumnsWidget.views(context, width, days = WeekDays.FROM_TODAY) }

    private fun rowImage(views: (Context, Int) -> RemoteViews = columns()): ImageView {
        val list = WidgetHost.applied(application, views = views).findViewById<ListView>(R.id.list)
        return list.adapter.getView(0, null, list).findViewById(R.id.week_light)
    }

    @Test
    fun `the list has one row, the seven days`() {
        val list = WidgetHost.applied(application, views = columns()).findViewById<ListView>(R.id.list)
        assertEquals(1, list.adapter.count)
        assertEquals(LocalDate.of(2026, 11, 9).toEpochDay(), list.adapter.getItemId(0))
    }

    @Test
    fun `a day's column names its day and sends that day to the tap activity`() {
        val list =
            WidgetHost
                .laidOut(
                    application,
                    WidgetHost.LOW_HEIGHT_DP,
                    columns(),
                ).findViewById<ListView>(R.id.list)
        val column = list.getChildAt(0).findViewById<View>(R.id.day_2)
        assertEquals("Mittwoch, 11. November", column.contentDescription)
        column.performClick()
        val started = shadowOf(application).nextStartedActivity
        assertEquals(TapActivity::class.java.name, started.component?.className)
        assertEquals(Tap.Day(wednesday), Taps.read(started))
    }

    @Test
    fun `the header and plus send their tap to the tap activity, plus with today`() {
        val widget = WidgetHost.applied(application, views = columns())
        widget.findViewById<View>(R.id.header).performClick()
        assertEquals(Tap.Header, Taps.read(checkNotNull(shadowOf(application).nextStartedActivity)))
        widget.findViewById<View>(R.id.fab).performClick()
        assertEquals(Tap.New(wednesday), Taps.read(checkNotNull(shadowOf(application).nextStartedActivity)))
    }

    @Test
    fun `the header names the week and its range, and its heads start on the first day shown`() {
        val widget = WidgetHost.applied(application, views = fromToday)
        assertEquals("Nächste 7 Tage, 11.–17. Nov.", widget.findViewById<View>(R.id.header).contentDescription)
        val header = (widget.findViewById<ImageView>(R.id.header_light).drawable as BitmapDrawable).bitmap
        val assets = RenderAssets.of(application)
        val chrome =
            ChromeRenderer(
                assets.fonts,
                assets.icons,
                application.resources.getStringArray(R.array.weekday_heads).toList(),
            )
        val scale = Scale(application.resources.displayMetrics.density)
        val widthPx = scale.px(WidgetHost.WIDTH_DP - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val draw = { from: DayOfWeek ->
            chrome.weekHeader("Nächste 7 Tage", "11.–17. Nov.", from, widthPx, scale, SampleData.light)
        }
        assertTrue(header.sameAs(draw(DayOfWeek.WEDNESDAY)))
        assertFalse(header.sameAs(draw(DayOfWeek.MONDAY)))
    }

    @Test
    fun `at its default height the row is taller than the list, so the fade shows and the row ends in its room`() {
        val widget = WidgetHost.applied(application, views = columns())
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.fade).visibility)
        val scale = Scale(application.resources.displayMetrics.density)
        val week = MonthLayout.week(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), SampleData.entries, wednesday)
        val rows =
            WeekRowRenderer(
                RenderAssets.of(application).fonts,
                RenderAssets.of(application).icons,
                RenderAssets.of(application).drawings,
            )
        val bitmap = (rowImage().drawable as BitmapDrawable).bitmap
        val expected = ceil(scale.hairline + scale.px(rows.heightDp(week) + WeekColumnsWidget.FADE_DP)).toInt()
        assertEquals(expected, bitmap.height)
    }

    @Test
    fun `when the list shows the whole row, there is no fade and no room below it`() {
        val widget = WidgetHost.applied(application, views = columns(WidgetHost.HEIGHT_DP))
        assertEquals(View.GONE, widget.findViewById<View>(R.id.fade).visibility)
        val scale = Scale(application.resources.displayMetrics.density)
        val week = MonthLayout.week(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), SampleData.entries, wednesday)
        val rows =
            WeekRowRenderer(
                RenderAssets.of(application).fonts,
                RenderAssets.of(application).icons,
                RenderAssets.of(application).drawings,
            )
        val bitmap = (rowImage(columns(WidgetHost.HEIGHT_DP)).drawable as BitmapDrawable).bitmap
        assertEquals(ceil(scale.hairline + scale.px(rows.heightDp(week))).toInt(), bitmap.height)
    }

    @Test
    fun `outside night mode the light drawings show and the fade ends in the light card's colour`() {
        assertModes(light = View.VISIBLE, dark = View.GONE)
        assertFade(SampleData.light.card)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the dark drawings show and the fade ends in the dark card's colour`() {
        assertModes(light = View.GONE, dark = View.VISIBLE)
        assertFade(SampleData.dark.card)
    }

    private fun assertModes(
        light: Int,
        dark: Int,
    ) {
        val widget = WidgetHost.applied(application, views = columns())
        assertEquals(light, widget.findViewById<View>(R.id.header_light).visibility)
        assertEquals(dark, widget.findViewById<View>(R.id.header_dark).visibility)
        assertEquals(light, widget.findViewById<View>(R.id.fab_light).visibility)
        assertEquals(dark, widget.findViewById<View>(R.id.fab_dark).visibility)
        val list = widget.findViewById<ListView>(R.id.list)
        val row = list.adapter.getView(0, null, list)
        assertEquals(light, row.findViewById<View>(R.id.week_light).visibility)
        assertEquals(dark, row.findViewById<View>(R.id.week_dark).visibility)
    }

    /** The fade runs from the card's colour, clear, to the card's colour. */
    private fun assertFade(card: Int) {
        assertEquals(card, application.getColor(R.color.widget_card))
        assertEquals(card and 0x00FFFFFF, application.getColor(R.color.widget_card_clear))
    }

    @Test
    fun `an update draws the widget at the height the launcher reports`() {
        val id = shadowOf(manager).createWidget(WeekColumnsWidgetProvider::class.java, R.layout.widget_week_columns)
        val widget = shadowOf(manager).getViewFor(id)
        for (image in listOf(R.id.header_light, R.id.header_dark, R.id.fab_light, R.id.fab_dark)) {
            assertTrue(widget.findViewById<ImageView>(image).drawable is BitmapDrawable)
        }
        val log = ShadowLog.getLogsForTag("WeekColumnsWidget").single().msg
        assertTrue(log, log.startsWith("Widget $id: drew 6 bitmaps at ") && log.endsWith(", faded"))
        val options =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 276)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 456)
            }
        manager.updateAppWidgetOptions(id, options)
        WeekColumnsWidgetProvider().onAppWidgetOptionsChanged(application, manager, id, options)
        assertFalse(
            ShadowLog
                .getLogsForTag("WeekColumnsWidget")
                .last()
                .msg
                .endsWith(", faded"),
        )
    }

    @Test
    fun `only the system may send the widget's broadcasts`() {
        val receiver =
            application.packageManager.getReceiverInfo(
                ComponentName(application, WeekColumnsWidgetProvider::class.java),
                0,
            )
        assertTrue(receiver.exported)
        assertEquals("android.permission.BIND_APPWIDGET", receiver.permission)
        assertEquals("Woche quer", receiver.loadLabel(application.packageManager).toString())
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    fun `the layout's measures fit the drawn parts`() {
        val scale = Scale(application.resources.displayMetrics.density)
        val px = { dp: Float -> scale.px(dp).roundToInt() }
        val widget = WidgetHost.laidOut(application, WidgetHost.LOW_HEIGHT_DP, columns())
        val list = widget.findViewById<ListView>(R.id.list)
        assertEquals(px(PhoneMonth.CARD_INSET), list.left + (list.parent as View).left)
        val week = list.getChildAt(0).findViewById<ImageView>(R.id.week_light)
        val bitmap = (week.drawable as BitmapDrawable).bitmap
        assertEquals(list.width, bitmap.width)
        assertEquals(week.height, bitmap.height)
        val header = widget.findViewById<View>(R.id.header)
        assertEquals(
            header.height,
            (widget.findViewById<ImageView>(R.id.header_light).drawable as BitmapDrawable).bitmap.height,
        )
        // The fade lies over the list's bottom edge, as wide as the list, as tall as the row's blank room.
        val fade = widget.findViewById<View>(R.id.fade)
        assertEquals(px(WeekColumnsWidget.FADE_DP.toFloat()), fade.height)
        assertEquals(list.left + (list.parent as View).left, fade.left)
        assertEquals(list.width, fade.width)
        assertEquals(list.bottom + (list.parent as View).top, fade.bottom)
        val fab = widget.findViewById<View>(R.id.fab)
        assertEquals(px(PhoneMonth.FAB_MARGIN), widget.width - fab.right)
        assertNotNull(fab.contentDescription)
    }
}
