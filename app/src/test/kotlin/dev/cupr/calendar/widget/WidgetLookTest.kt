package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.WeekDays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Each widget drawn by its settings: its calendars, days and corner of "+", and when its list returns to today. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetLookTest {
    @get:Rule
    val sampleContent = SampleContent()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(context)
    private val shadow = shadowOf(manager)
    private val settings get() = Settings.widgets(context)
    private val scrolls get() = Settings.lastScrolls(context)
    private val now = Instant.parse("2026-11-17T10:00:00Z")

    /** A provider of kind that draws nothing and keeps what it was asked to draw. */
    private class Recording(
        override val kind: WidgetKind,
    ) : DrawnWidgetProvider() {
        override val tag = "Recording"
        val drawn = mutableListOf<Pair<Content.Calendar, Look>>()

        override fun update(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            calendar: Content.Calendar,
            look: Look,
        ) {
            drawn += calendar to look
        }
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.WARN)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
    }

    @After
    fun tearDown() = WorkManagerTestInitHelper.closeWorkDatabase()

    @Test
    fun `on each update the list scrolls at every redraw`() {
        for (redraw in Redraw.entries) {
            assertTrue("$redraw", ReturnToToday.due(BackToToday.EACH_UPDATE, redraw, now, now))
        }
    }

    @Test
    fun `only at midnight the list scrolls at the midnight's redraw, and after saving`() {
        assertFalse(ReturnToToday.due(BackToToday.MIDNIGHT, Redraw.UPDATE, null, now))
        assertTrue(ReturnToToday.due(BackToToday.MIDNIGHT, Redraw.MIDNIGHT, now, now))
        assertTrue(ReturnToToday.due(BackToToday.MIDNIGHT, Redraw.SAVED, now, now))
    }

    @Test
    fun `never the list scrolls, but after saving`() {
        assertFalse(ReturnToToday.due(BackToToday.NEVER, Redraw.UPDATE, null, now))
        assertFalse(ReturnToToday.due(BackToToday.NEVER, Redraw.MIDNIGHT, null, now))
        assertTrue(ReturnToToday.due(BackToToday.NEVER, Redraw.SAVED, now, now))
    }

    @Test
    fun `an interval is due once that long has passed since the last scroll, or with none yet`() {
        for (
        (setting, minutes) in
        listOf(
            BackToToday.EVERY_15_MINUTES to 15L,
            BackToToday.EVERY_30_MINUTES to 30L,
            BackToToday.EVERY_60_MINUTES to 60L,
        )
        ) {
            val interval = Duration.ofMinutes(minutes)
            for (redraw in listOf(Redraw.UPDATE, Redraw.MIDNIGHT)) {
                assertTrue("$setting", ReturnToToday.due(setting, redraw, null, now))
                assertTrue("$setting", ReturnToToday.due(setting, redraw, now - interval, now))
                assertFalse("$setting", ReturnToToday.due(setting, redraw, now - interval + Duration.ofSeconds(1), now))
            }
            assertTrue("$setting", ReturnToToday.due(setting, Redraw.SAVED, now, now))
        }
    }

    @Test
    fun `a widget draws without the calendars it leaves out, with its days and its corner of plus`() {
        val provider = Recording(WidgetKind.WEEK)
        settings.write(
            4,
            WidgetSettings(
                leftOut = setOf("calendar.model", "calendar.contact_birthdays"),
                days = WeekDays.FROM_TODAY,
                plusCorner = PlusCorner.BOTTOM_LEFT,
            ),
        )

        provider.onUpdate(context, manager, intArrayOf(4, 5))

        val (left, look) = provider.drawn[0]
        val all = SampleData.calendar().entries
        assertEquals(
            all.filter {
                it.calendar != "calendar.model" && it.calendar != "calendar.contact_birthdays"
            },
            left.entries,
        )
        assertTrue(left.entries.size < all.size)
        assertEquals(Look(WeekDays.FROM_TODAY, PlusCorner.BOTTOM_LEFT, scroll = true), look)
        assertEquals(SampleData.calendar() to Look(), provider.drawn[1])
    }

    @Test
    fun `a calendar without calendars left out stays as it is`() {
        val calendar = SampleData.calendar()

        assertTrue(calendar.without(emptySet()) === calendar)
        assertEquals(calendar.entries, calendar.without(setOf("calendar.none")).entries)
    }

    @Test
    fun `the time of each scroll to today is kept, and an interval scrolls again once it has passed`() {
        val provider = Recording(WidgetKind.MONTH)
        settings.write(4, WidgetSettings(backToToday = BackToToday.EVERY_30_MINUTES))

        provider.onUpdate(context, manager, intArrayOf(4))
        val first = checkNotNull(scrolls.read(4))
        provider.onUpdate(context, manager, intArrayOf(4))
        assertEquals(first, scrolls.read(4))
        val earlier = Instant.now() - Duration.ofMinutes(31)
        scrolls.write(4, earlier)
        provider.onUpdate(context, manager, intArrayOf(4))

        assertEquals(listOf(true, false, true), provider.drawn.map { it.second.scroll })
        assertTrue(Duration.between(first, Instant.now()) < Duration.ofMinutes(1))
        assertTrue(checkNotNull(scrolls.read(4)).isAfter(earlier))
    }

    @Test
    fun `a list that does not scroll keeps no time, and woche quer never scrolls`() {
        val month = Recording(WidgetKind.MONTH)
        settings.write(4, WidgetSettings(backToToday = BackToToday.NEVER))
        month.onUpdate(context, manager, intArrayOf(4))
        val columns = Recording(WidgetKind.WEEK_COLUMNS)
        columns.updateAll(context, manager, intArrayOf(5), SampleData.calendar(), Redraw.SAVED)

        assertFalse(
            month.drawn
                .single()
                .second.scroll,
        )
        assertFalse(
            columns.drawn
                .single()
                .second.scroll,
        )
        assertNull(scrolls.read(4))
        assertNull(scrolls.read(5))
    }

    @Test
    fun `set never, the month's list keeps its position on an update but keeps its padding`() {
        val id = shadow.createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        settings.write(id, WidgetSettings(backToToday = BackToToday.NEVER))
        shadow.setAlwaysRecreateViewsDuringUpdate(true)
        ShadowLog.clear()

        Widgets.update(context)

        val list = shadow.getViewFor(id).findViewById<ListView>(R.id.list)
        assertEquals(0, shadowOf(list).smoothScrolledPosition)
        val log = ShadowLog.getLogsForTag(MonthWidget.TAG).single { it.type == Log.INFO }.msg
        assertTrue(log, log.endsWith("; today is row 2, keeps its position: not scrolled to today: no height"))
    }

    @Test
    fun `set never, a widget of a reported height keeps its position too, padded for today on top`() {
        val id = shadow.createWidget(WeekWidgetProvider::class.java, R.layout.widget_week)
        settings.write(id, WidgetSettings(backToToday = BackToToday.NEVER))
        shadow.setAlwaysRecreateViewsDuringUpdate(true)
        val options =
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
            }
        manager.updateAppWidgetOptions(id, options)
        ShadowLog.clear()

        WeekWidgetProvider().onAppWidgetOptionsChanged(context, manager, id, options)

        val list = shadow.getViewFor(id).findViewById<ListView>(R.id.list)
        assertEquals(0, shadowOf(list).smoothScrolledPosition)
        assertEquals(84, list.paddingBottom)
        val log = ShadowLog.getLogsForTag(WeekWidget.TAG).single { it.type == Log.INFO }.msg
        assertTrue(
            log,
            log.endsWith(
                "; today is row 2, keeps its position: not scrolled to today, bottom padding 84 px; " +
                    "300 dp (appWidgetMaxHeight, 200 dp least)",
            ),
        )
    }

    @Test
    fun `set only at midnight, the month's list scrolls at midnight, not on an update`() {
        val id = shadow.createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        settings.write(id, WidgetSettings(backToToday = BackToToday.MIDNIGHT))
        shadow.setAlwaysRecreateViewsDuringUpdate(true)

        Widgets.update(context)
        val list = { shadow.getViewFor(id).findViewById<ListView>(R.id.list) }
        assertEquals(0, shadowOf(list()).smoothScrolledPosition)
        TestListenableWorkerBuilder<MidnightWorker>(context).build().doWork()

        assertEquals(2, shadowOf(list()).smoothScrolledPosition)
    }

    @Test
    fun `saving redraws that widget alone, its list back at today`() {
        val ids =
            List(2) { shadow.createWidget(WeekWidgetProvider::class.java, R.layout.widget_week) }
        for (id in ids) settings.write(id, WidgetSettings(backToToday = BackToToday.NEVER))
        shadow.setAlwaysRecreateViewsDuringUpdate(true)
        ShadowLog.clear()

        assertTrue(Widgets.saved(context, ids[0], WidgetKind.WEEK))

        val drawn = ShadowLog.getLogsForTag(WeekWidget.TAG).filter { it.type == Log.INFO }.map { it.msg }
        assertEquals(1, drawn.size)
        assertTrue(drawn.single(), drawn.single().startsWith("Widget ${ids[0]}: "))
        assertTrue(drawn.single(), drawn.single().endsWith("; today is row 2, scrolls it into view: no height"))
        assertEquals(2, shadowOf(shadow.getViewFor(ids[0]).findViewById<ListView>(R.id.list)).smoothScrolledPosition)
        assertTrue(scrolls.read(ids[0]) != null)
    }

    @Test
    fun `saving for an id that is no widget of its kind redraws nothing`() {
        val month = shadow.createWidget(MonthWidgetProvider::class.java, R.layout.widget_month)
        ShadowLog.clear()

        assertFalse(Widgets.saved(context, 99, WidgetKind.WEEK))
        assertFalse(Widgets.saved(context, month, WidgetKind.WEEK))

        assertTrue(ShadowLog.getLogsForTag(MonthWidget.TAG).isEmpty())
        assertTrue(ShadowLog.getLogsForTag(WeekWidget.TAG).isEmpty())
    }

    @Test
    fun `plus stands at the bottom left when set so, at the bottom right by default`() {
        val scale = Scale(context.resources.displayMetrics.density)
        val margin = scale.px(PhoneMonth.FAB_MARGIN).roundToInt()
        val left =
            WidgetHost.laidOut(context, views = { c, width ->
                MonthWidget
                    .draw(
                        c,
                        width,
                        1,
                        Long.MAX_VALUE,
                        SampleData.calendar(),
                        look = Look(corner = PlusCorner.BOTTOM_LEFT),
                    ).views
            })
        val right = WidgetHost.laidOut(context)

        val atLeft = WidgetHost.boundsIn(left, left.findViewById<View>(R.id.fab_left))
        assertEquals(margin, atLeft.left)
        assertEquals(margin, left.height - atLeft.bottom)
        assertEquals(0, WidgetHost.boundsIn(left, left.findViewById<View>(R.id.fab_left_light)).left)
        assertTrue(left.findViewById<ImageView>(R.id.fab_left_light).drawable is BitmapDrawable)
        assertEquals(View.GONE, left.findViewById<View>(R.id.plus_right).visibility)
        assertEquals(Tap.New(SampleData.calendar().today), tapOf(left.findViewById(R.id.fab_left)))
        val atRight = WidgetHost.boundsIn(right, right.findViewById<View>(R.id.fab))
        assertEquals(margin, right.width - atRight.right)
        assertEquals(View.GONE, right.findViewById<View>(R.id.plus_left).visibility)
    }

    @Test
    fun `outside night mode the left plus shows its light drawing`() {
        val widget = WidgetHost.applied(context) { c, width -> MonthWidget.views(c, width, corner = PlusCorner.BOTTOM_LEFT) }

        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.fab_left_light).visibility)
        assertEquals(View.GONE, widget.findViewById<View>(R.id.fab_left_dark).visibility)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the left plus shows its dark drawing`() {
        val widget = WidgetHost.applied(context) { c, width -> MonthWidget.views(c, width, corner = PlusCorner.BOTTOM_LEFT) }

        assertEquals(View.GONE, widget.findViewById<View>(R.id.fab_left_light).visibility)
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.fab_left_dark).visibility)
    }

    /** What a tap on view sends to the tap activity. */
    private fun tapOf(view: View): Tap? {
        view.performClick()
        return Taps.read(checkNotNull(shadowOf(context).nextStartedActivity))
    }

    @Test
    fun `the corner of plus is set on each update, so going back to the right takes effect`() {
        val widget =
            WidgetHost.laidOut(context, views = { c, width ->
                WeekColumnsWidget
                    .draw(
                        c,
                        width,
                        WeekColumnsWidget.DEFAULT_HEIGHT_DP,
                        Look(corner = PlusCorner.BOTTOM_LEFT),
                        1,
                        Long.MAX_VALUE,
                        SampleData.calendar(),
                    ).views
            })
        assertEquals(
            Scale(context.resources.displayMetrics.density).px(PhoneMonth.FAB_MARGIN).roundToInt(),
            WidgetHost.boundsIn(widget, widget.findViewById<View>(R.id.fab_left)).left,
        )

        WidgetHost.reapplied(context, widget) { c, width -> WeekColumnsWidget.views(c, width) }

        assertEquals(View.GONE, widget.findViewById<View>(R.id.plus_left).visibility)
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.plus_right).visibility)
        val fab = WidgetHost.boundsIn(widget, widget.findViewById<View>(R.id.fab))
        assertEquals(
            Scale(context.resources.displayMetrics.density).px(PhoneMonth.FAB_MARGIN).roundToInt(),
            widget.width - fab.right,
        )
    }
}
