package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
import dev.cupr.calendar.render.Probe
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.WeekDay
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.LocalDate

/** What the drawn widgets share: the bitmap limit, the cap that keeps a full week within it, and the fallback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawnWidgetTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(application)
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
        assertEquals(6L * 411 * 914, BitmapLimit.limitBytes(application, "Test", 1))
    }

    @Test
    fun `a display without a size limits nothing, and the log says so`() {
        assertEquals(Long.MAX_VALUE, BitmapLimit.limitOf(0, 914, "Test", 1))
        assertEquals(Long.MAX_VALUE, BitmapLimit.limitOf(411, -1, "Test", 2))
        assertEquals(
            listOf(
                "Widget 1: the display reports no size (0 x 914), so its bitmaps are not limited",
                "Widget 2: the display reports no size (411 x -1), so its bitmaps are not limited",
            ),
            ShadowLog.getLogsForTag("Test").map { it.msg },
        )
    }

    @Test
    fun `the sample's widgets stay within the phone's limit and leave nothing out`() {
        val limit = BitmapLimit.limitBytes(application, "Test", 1)
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
            "Widget 7: its bitmaps take ${unlimited.bytes} bytes, over the limit of $limit; " +
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
        val expected =
            Regex(
                "Widget 8: its bitmaps take ${unlimited.bytes} bytes, over the limit of $limit; " +
                    "(\\d+) entries left out",
            )
        val left = checkNotNull(expected.matchEntire(warning)) { warning }.groupValues[1].toInt()
        assertTrue("$left", left > 0)
        val week = MonthLayout.week(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), full, wednesday)
        val capped = BitmapLimit.capped(week, 0, { 1L }, WeekColumnsWidget::leaveOutOne)
        assertTrue(capped.drawn.days.all { it.shown.isEmpty() })
        assertEquals(week.days.map { it.shown.size }, capped.drawn.days.map { it.more })
        assertEquals(week.days.sumOf { it.shown.size }, capped.left)
        val one = BitmapLimit.capped(week, 0, { if (it.days[5].more == 0) 1L else 0L }, WeekColumnsWidget::leaveOutOne)
        assertEquals(1, one.drawn.days[5].more)
        assertEquals(1, one.left)
        assertEquals(week.days[5].shown.dropLast(1), one.drawn.days[5].shown)
    }

    @Test
    fun `the cap takes from the day showing the most entries, one at a time, until the week fits`() {
        val days = WeekLayout.build(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), full, wednesday)
        val untouched = BitmapLimit.capped(days, Long.MAX_VALUE, { 1L }, WeekWidget::leaveOutOne)
        assertEquals(days, untouched.drawn)
        assertEquals(0, untouched.left)
        // A day costs as many bytes as it shows entries; room for 8 fewer than all.
        val total = days.sumOf { it.entries.size }
        val shownCount = { week: List<WeekDay> -> week.sumOf { it.shown.size }.toLong() }
        val capped = BitmapLimit.capped(days, total - 8L, shownCount, WeekWidget::leaveOutOne)
        assertEquals(listOf(0, 0, 0, 0, 0, 8, 0), capped.drawn.map { it.more })
        assertEquals(8, capped.left)
        // With no room at all, every day leaves out all of its entries, and the cap ends.
        val none = BitmapLimit.capped(days, -1, shownCount, WeekWidget::leaveOutOne)
        assertEquals(days.map { it.entries.size }, none.drawn.map { it.more })
        assertEquals(total, none.left)
    }

    @Test
    fun `of two busy days the cap takes from whichever shows more, the first of them on a tie`() {
        val tuesday = LocalDate.of(2026, 11, 10)
        val thursday = LocalDate.of(2026, 11, 12)
        val entries = (8 until 13).map { halfHour(tuesday, it) } + (8 until 14).map { halfHour(thursday, it) }
        val days = WeekLayout.build(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), entries, wednesday)
        assertEquals(listOf(0, 5, 0, 6, 0, 0, 0), days.map { it.shown.size })
        val once = checkNotNull(WeekWidget.leaveOutOne(days))
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 0), once.map { it.more })
        val twice = checkNotNull(WeekWidget.leaveOutOne(once))
        assertEquals(listOf(0, 1, 0, 1, 0, 0, 0), twice.map { it.more })
    }

    @Test
    fun `woche quer takes from the day with the most lines, counting its +N line`() {
        val monday = LocalDate.of(2026, 11, 9)
        val four = List(4) { Probe.allDay(monday) }
        // Monday shows four, Tuesday four and "+1": Tuesday has more lines.
        val week =
            Probe.week(
                monday,
                mapOf(
                    0 to Probe.day(monday, shown = four),
                    1 to Probe.day(monday.plusDays(1), shown = four, more = 1),
                ),
            )
        val less = checkNotNull(WeekColumnsWidget.leaveOutOne(week))
        assertEquals(listOf(4, 3), less.days.take(2).map { it.shown.size })
        assertEquals(listOf(0, 2), less.days.take(2).map { it.more })
        assertThrows(IllegalStateException::class.java) { Probe.day(monday).leaveOutLast() }
        // Two days with as many lines: the first of them.
        val tie =
            Probe.week(
                monday,
                mapOf(0 to Probe.day(monday, shown = four), 1 to Probe.day(monday.plusDays(1), shown = four)),
            )
        assertEquals(listOf(3, 4), checkNotNull(WeekColumnsWidget.leaveOutOne(tie)).days.take(2).map { it.shown.size })
    }

    @Test
    fun `a week still over the limit when nothing is left to leave out says how far`() {
        val unlimited = WeekWidget.draw(application, 276, WeekDays.MONDAY_TO_SUNDAY, 7, Long.MAX_VALUE, full)
        val drawn = WeekWidget.draw(application, 276, WeekDays.MONDAY_TO_SUNDAY, 7, 1000, full)
        val all =
            WeekLayout
                .build(
                    WeekDays.MONDAY_TO_SUNDAY.dates(wednesday),
                    full,
                    wednesday,
                ).sumOf { it.entries.size }
        val warning = ShadowLog.getLogsForTag("WeekWidget").single { it.type == Log.WARN }.msg
        assertEquals(
            "Widget 7: its bitmaps take ${unlimited.bytes} bytes, over the limit of 1000; " +
                "$all entries left out, still ${drawn.bytes - 1000} bytes over",
            warning,
        )
    }

    @Test
    fun `the month over the limit warns, leaving nothing out`() {
        val drawn = MonthWidget.draw(application, 276, 3, 1000)
        assertEquals(
            "Widget 3: its bitmaps take ${drawn.bytes} bytes, over the limit of 1000; " +
                "0 entries left out, still ${drawn.bytes - 1000} bytes over",
            ShadowLog.getLogsForTag("MonthWidget").single { it.type == Log.WARN }.msg,
        )
    }

    @Test
    fun `a widget's height is above 0, and so is its least height when it has one`() {
        assertThrows(IllegalArgumentException::class.java) { WidgetHeight(0, null) }
        assertThrows(IllegalArgumentException::class.java) { WidgetHeight(1, 0) }
        assertEquals("1 dp (appWidgetMaxHeight, 1 dp least)", WidgetHeight(1, 1).forLog)
    }

    @Test
    fun `a drawing exactly at the limit does not warn, one byte over does`() {
        BitmapLimit.warnOver("Test", 1, needed = 10, limit = 10, left = 0, after = 10)
        assertTrue(ShadowLog.getLogsForTag("Test").isEmpty())
        BitmapLimit.warnOver("Test", 1, needed = 11, limit = 10, left = 1, after = 9)
        assertEquals(
            listOf("Widget 1: its bitmaps take 11 bytes, over the limit of 10; 1 entry left out"),
            ShadowLog.getLogsForTag("Test").map { it.msg },
        )
    }

    @Test
    fun `without a limit the log says no limit`() {
        val kept = Kept()
        DrawnWidget.logDrawn("Test", 1, kept, 100, Long.MAX_VALUE)
        assertEquals(
            "Widget 1: drew 0 bitmaps at 100px wide, 0 bytes of no limit",
            ShadowLog.getLogsForTag("Test").single().msg,
        )
    }

    @Test
    fun `marks with no room beside their number are logged once per drawing`() {
        WeekColumnsWidget.draw(application, 150, 220, WeekDays.MONDAY_TO_SUNDAY, 9, Long.MAX_VALUE)
        val marks = ShadowLog.getLogsForTag("WeekColumnsWidget").map { it.msg }.filter { "no room" in it }
        assertEquals(listOf("Widget 9: 2026-11-11 has no room beside its number for 1 of its marks"), marks)
    }

    @Test
    fun `the month logs its marks with no room once per drawing too`() {
        MonthWidget.draw(application, 150, 4, Long.MAX_VALUE)
        val marks = ShadowLog.getLogsForTag("MonthWidget").map { it.msg }.filter { "no room" in it }
        assertTrue(marks.isNotEmpty())
        assertEquals(marks.distinct(), marks)
        assertTrue(marks.toString(), marks.all { it.startsWith("Widget 4: 2026-") })
    }

    @Test
    fun `each widget names its own too-full text`() {
        assertEquals("Monat zu voll – im Kalender öffnen", application.getString(MonthWidget.TOO_FULL))
        assertEquals("Woche zu voll – im Kalender öffnen", application.getString(WeekWidget.TOO_FULL))
        assertEquals("Woche zu voll – im Kalender öffnen", application.getString(WeekColumnsWidget.TOO_FULL))
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
        val text = shown.findViewById<TextView>(R.id.plain_text)
        assertEquals("Woche zu voll – im Kalender öffnen", text.text.toString())
        text.performClick()
        assertEquals(Tap.Header, Taps.read(checkNotNull(shadowOf(application).nextStartedActivity)))
        assertEquals(
            "Widget 5: the system refused its views; the app counted 42 bytes of bitmaps against 41, " +
                "the limit it read from the display",
            ShadowLog.getLogsForTag("Test").single { it.type == Log.ERROR }.msg,
        )
    }

    @Test
    fun `a plain text the system refuses as well is not caught again`() {
        val drawn = Drawn(RemoteViews(application.packageName, R.layout.widget_week), 42, 41)
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                DrawnWidget.push(application, 5, drawn, R.string.widget_week_too_full, "Test") {
                    throw IllegalArgumentException("refused")
                }
            }
        assertEquals("refused", error.message)
    }

    @Test
    fun `views the system takes are handed over once, without the fallback`() {
        val pushed = mutableListOf<RemoteViews>()
        val views = RemoteViews(application.packageName, R.layout.widget_week)
        DrawnWidget.push(application, 5, Drawn(views, 1, 2), R.string.widget_week_too_full, "Test") { pushed += it }
        assertEquals(listOf(views), pushed)
    }

    @Test
    fun `a widget whose drawing fails shows that it could not be drawn, and the others still update`() {
        val shadow = shadowOf(manager)
        // Created by a provider that draws nothing, in a layout without a list, so only the update below gives the
        // fine one its list.
        val ids = List(3) { shadow.createWidget(AppWidgetProvider::class.java, R.layout.widget_plain) }
        assertTrue(ids.all { shadow.getViewFor(it).findViewById<View>(R.id.list) == null })
        val (argument, state, fine) = ids
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
                    // What a model's require and a drawing's check throw.
                    require(id != argument) { "a require in the drawing" }
                    check(id != state) { "a check in the drawing" }
                    WeekWidget.update(context, manager, id)
                }
            }
        provider.onUpdate(application, manager, ids.toIntArray())
        assertEquals(ids, updated)
        for (id in listOf(argument, state)) {
            val text = shadow.getViewFor(id).findViewById<TextView>(R.id.plain_text)
            assertEquals("Kalender konnte nicht gezeichnet werden – im Kalender öffnen", text.text.toString())
            text.performClick()
            assertEquals(Tap.Header, Taps.read(checkNotNull(shadowOf(application).nextStartedActivity)))
        }
        assertTrue(shadow.getViewFor(fine).findViewById<View>(R.id.list) is ListView)
        val errors = ShadowLog.getLogsForTag("Test").filter { it.type == Log.ERROR }
        assertEquals(
            listOf("Widget $argument: could not be drawn", "Widget $state: could not be drawn"),
            errors.map { it.msg },
        )
        assertEquals(listOf("a require in the drawing", "a check in the drawing"), errors.map { it.throwable.message })
        // A size change goes the same way.
        provider.onAppWidgetOptionsChanged(application, manager, argument, android.os.Bundle())
        assertEquals(3, ShadowLog.getLogsForTag("Test").count { it.type == Log.ERROR })
    }

    @Test
    fun `any other exception is not caught by the provider`() {
        val provider =
            object : DrawnWidgetProvider() {
                override val tag = "Test"

                override fun update(
                    context: Context,
                    manager: AppWidgetManager,
                    id: Int,
                ): Unit = throw UnsupportedOperationException("not caught")
            }
        val id = shadowOf(manager).createWidget(AppWidgetProvider::class.java, R.layout.widget_plain)
        assertThrows(
            UnsupportedOperationException::class.java,
        ) { provider.onUpdate(application, manager, intArrayOf(id)) }
    }

    @Test
    fun `the too-full text is the palette's text on the card`() {
        assertEquals(SampleData.light.text, application.getColor(R.color.widget_text))
        val views = DrawnWidget.plainViews(application, R.string.widget_month_too_full)
        val text = views.apply(application, FrameLayout(application)).findViewById<TextView>(R.id.plain_text)
        assertEquals("Monat zu voll – im Kalender öffnen", text.text.toString())
        assertEquals(View.VISIBLE, text.visibility)
        assertEquals(SampleData.light.text, text.currentTextColor)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the too-full text is the dark palette's text`() {
        assertEquals(SampleData.dark.text, application.getColor(R.color.widget_text))
        val views = DrawnWidget.plainViews(application, R.string.widget_month_too_full)
        val text = views.apply(application, FrameLayout(application)).findViewById<TextView>(R.id.plain_text)
        assertEquals(SampleData.dark.text, text.currentTextColor)
    }

    /** A timed entry from hour to half past it on date. */
    private fun halfHour(
        date: LocalDate,
        hour: Int,
    ) = Probe.timed(date, icon = null).copy(start = date.atTime(hour, 0), end = date.atTime(hour, 30))

    private companion object {
        const val FULL_DAY = 30
    }
}
