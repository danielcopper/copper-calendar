package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.LocalDate

/** What the drawn widgets share: the bitmap limit, the cap that keeps a full week within it, and the fallback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawnWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val wednesday = LocalDate.of(2026, 11, 11)
    private val saturday = LocalDate.of(2026, 11, 14)

    /** The sample week and 30 more timed entries on Saturday. */
    private val full =
        SampleData.entries +
            List(FULL_DAY) {
                Entry(
                    start = saturday.atTime(6, 0).plusMinutes(it * 30L),
                    end = saturday.atTime(6, 30).plusMinutes(it * 30L),
                    allDay = false,
                    title = "Termin ${it + 1}",
                    calendar = SampleData.FAMILY,
                    uid = "full-$it",
                )
            }

    @Test
    fun `the limit is 6 times the display's pixels`() {
        // robolectric.properties: a 411 x 914 dp screen, here at 1 px per dp.
        assertEquals(6L * 411 * 914, DrawnWidget.limitBytes(application))
    }

    @Test
    fun `the sample's widgets stay within the phone's limit and leave nothing out`() {
        val limit = DrawnWidget.limitBytes(application)
        assertTrue(WeekWidget.draw(application, 276, WeekDays.MONDAY_TO_SUNDAY, 1, limit).bytes <= limit)
        assertTrue(WeekColumnsWidget.draw(application, 276, 220, WeekDays.MONDAY_TO_SUNDAY, 2, limit).bytes <= limit)
        assertTrue(MonthWidget.draw(application, 276, 3, limit).bytes <= limit)
        for (tag in listOf("WeekWidget", "WeekColumnsWidget", "MonthWidget")) {
            assertTrue(tag, ShadowLog.getLogsForTag(tag).none { it.type == Log.WARN })
        }
    }

    @Test
    fun `a week over the limit leaves out the fullest day's last entries for a line counting them`() {
        val unlimited = WeekWidget.draw(application, 276, WeekDays.MONDAY_TO_SUNDAY, 7, Long.MAX_VALUE, full)
        val limit = unlimited.bytes * 2 / 3
        val drawn = WeekWidget.draw(application, 276, WeekDays.MONDAY_TO_SUNDAY, 7, limit, full)
        assertTrue("${drawn.bytes} of $limit", drawn.bytes <= limit)
        val list =
            WidgetHost.applied(application, views = { _, _ -> drawn.views }).findViewById<ListView>(R.id.list)
        val entries = list.adapter.getView(5, null, list).findViewById<ViewGroup>(R.id.entries)
        val shown = entries.childCount
        assertTrue("$shown entries shown", shown in 1 until 8 + FULL_DAY)
        // The other days keep all theirs: Wednesday's three.
        assertEquals(
            3,
            list.adapter
                .getView(2, null, list)
                .findViewById<ViewGroup>(R.id.entries)
                .childCount,
        )
        val warning = ShadowLog.getLogsForTag("WeekWidget").single { it.type == Log.WARN }.msg
        assertEquals(
            "Widget 7: its rows take ${unlimited.bytes} bytes, over the limit of $limit; " +
                "${8 + FULL_DAY - shown} entries left out",
            warning,
        )
    }

    @Test
    fun `woche quer over the limit leaves out the tallest day's last entries for its +N`() {
        val unlimited =
            WeekColumnsWidget.draw(application, 276, 220, WeekDays.MONDAY_TO_SUNDAY, 8, Long.MAX_VALUE, full)
        val limit = unlimited.bytes / 2
        val drawn = WeekColumnsWidget.draw(application, 276, 220, WeekDays.MONDAY_TO_SUNDAY, 8, limit, full)
        assertTrue("${drawn.bytes} of $limit", drawn.bytes <= limit)
        val warning = ShadowLog.getLogsForTag("WeekColumnsWidget").single { it.type == Log.WARN }.msg
        assertTrue(warning, warning.startsWith("Widget 8: its row takes ${unlimited.bytes} bytes, over the limit"))
        val week = MonthLayout.week(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), full, wednesday)
        val capped = WeekColumnsWidget.capped(week, 0) { 1 }
        assertTrue(capped.days.all { it.shown.isEmpty() })
        assertEquals(week.days.map { it.shown.size }, capped.days.map { it.more })
        val one = WeekColumnsWidget.capped(week, 0) { if (it.days[5].more == 0) 1 else 0 }
        assertEquals(1, one.days[5].more)
        assertEquals(week.days[5].shown.dropLast(1), one.days[5].shown)
    }

    @Test
    fun `the cap takes from the day showing the most entries, one at a time, until the week fits`() {
        val days = WeekLayout.build(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), full, wednesday)
        assertEquals(days, WeekWidget.capped(days, Long.MAX_VALUE) { 1 })
        // A day costs as many bytes as it shows entries; room for 8 fewer than all.
        val total = days.sumOf { it.entries.size }
        val capped = WeekWidget.capped(days, total - 8L) { it.shown.size.toLong() }
        assertEquals(listOf(0, 0, 0, 0, 0, 8, 0), capped.map { it.more })
        // With no room at all, every day leaves out all of its entries, and the cap ends.
        val none = WeekWidget.capped(days, -1) { it.shown.size.toLong() }
        assertEquals(days.map { it.entries.size }, none.map { it.more })
    }

    @Test
    fun `when the system refuses the views, the widget shows that it is too full and opens the calendar page`() {
        val pushed = mutableListOf<RemoteViews>()
        val drawn = Drawn(RemoteViews(application.packageName, R.layout.widget_week), 42, 41)
        DrawnWidget.push(application, 5, drawn, R.string.widget_week_too_full, "Test") { views ->
            pushed += views
            if (pushed.size == 1) throw IllegalArgumentException("RemoteViews for widget update exceeds")
        }
        assertEquals(2, pushed.size)
        val shown = pushed[1].apply(application, FrameLayout(application))
        val text = shown.findViewById<TextView>(R.id.too_full)
        assertEquals("Woche zu voll – im Kalender öffnen", text.text.toString())
        text.performClick()
        assertEquals(Tap.Header, Taps.read(checkNotNull(shadowOf(application).nextStartedActivity)))
        assertEquals(
            "Widget 5: refused with 42 bytes of bitmaps, limit 41",
            ShadowLog.getLogsForTag("Test").single { it.type == Log.ERROR }.msg,
        )
    }

    @Test
    fun `views the system takes are handed over once, without the fallback`() {
        val pushed = mutableListOf<RemoteViews>()
        val views = RemoteViews(application.packageName, R.layout.widget_week)
        DrawnWidget.push(application, 5, Drawn(views, 1, 2), R.string.widget_week_too_full, "Test") { pushed += it }
        assertEquals(listOf(views), pushed)
    }

    @Test
    fun `one widget the system refuses does not keep the others from their update`() {
        val updated = mutableListOf<Int>()
        val provider =
            object : DrawnWidgetProvider() {
                override val tag = "Test"

                override fun update(
                    context: Context,
                    manager: AppWidgetManager,
                    id: Int,
                ) {
                    updated += id
                    if (id == 1) throw IllegalArgumentException("refused")
                }
            }
        provider.onUpdate(application, AppWidgetManager.getInstance(application), intArrayOf(1, 2))
        assertEquals(listOf(1, 2), updated)
        assertEquals("Widget 1: not updated", ShadowLog.getLogsForTag("Test").single().msg)
    }

    @Test
    fun `the too-full text is the palette's text on the card`() {
        assertEquals(SampleData.light.text, application.getColor(R.color.widget_text))
        val views = DrawnWidget.tooFullViews(application, R.string.widget_month_too_full)
        val text = views.apply(application, FrameLayout(application)).findViewById<TextView>(R.id.too_full)
        assertEquals("Monat zu voll – im Kalender öffnen", text.text.toString())
        assertEquals(View.VISIBLE, text.visibility)
    }

    private companion object {
        const val FULL_DAY = 30
    }
}
