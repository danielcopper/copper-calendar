package dev.cupr.calendar.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.render.DayRowRenderer
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.Probe
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.TileWeek
import dev.cupr.calendar.render.WeekTexts
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.TapActivity
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
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
class WeekWidgetTest {
    private companion object {
        const val ROW_PX = 600
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = AppWidgetManager.getInstance(application)
    private val week: (android.content.Context, Int) -> android.widget.RemoteViews =
        { context, width -> WeekWidget.views(context, width) }
    private val fromToday: (android.content.Context, Int) -> android.widget.RemoteViews =
        { context, width -> WeekWidget.views(context, width, WeekDays.FROM_TODAY) }
    private val wednesday = LocalDate.of(2026, 11, 11)

    /** The laid-out widget's row of a day of the week (0 = Monday). */
    private fun row(index: Int): View {
        val list = WidgetHost.laidOut(application, views = week).findViewById<ListView>(R.id.list)
        return list.getChildAt(index - list.firstVisiblePosition)
    }

    /** What a tap on view sends to the tap activity. */
    private fun tapOf(view: View): Tap? {
        assertTrue("no click listener on ${view.contentDescription}", view.hasOnClickListeners())
        view.performClick()
        val started = shadowOf(application).nextStartedActivity
        assertNotNull("the tap started nothing", started)
        assertEquals(TapActivity::class.java.name, started.component?.className)
        return Taps.read(started)
    }

    @Test
    fun `the list has one row per day, Monday to Sunday or from today`() {
        assertEquals(
            7,
            WidgetHost
                .applied(application, views = week)
                .findViewById<ListView>(R.id.list)
                .adapter.count,
        )
        val list = WidgetHost.applied(application, views = fromToday).findViewById<ListView>(R.id.list)
        assertEquals(7, list.adapter.count)
        assertEquals(LocalDate.of(2026, 11, 17).toEpochDay(), list.adapter.getItemId(6))
    }

    @Test
    fun `the header names the week and its range`() {
        val header = WidgetHost.applied(application, views = week).findViewById<View>(R.id.header)
        assertEquals("Diese Woche, 9.–15. Nov.", header.contentDescription)
        val next = WidgetHost.applied(application, views = fromToday).findViewById<View>(R.id.header)
        assertEquals("Nächste 7 Tage, 11.–17. Nov.", next.contentDescription)
    }

    @Test
    fun `a day's row names its day and opens it`() {
        val day = row(2).findViewById<View>(R.id.day_row)
        assertEquals("Mittwoch, 11. November", day.contentDescription)
        assertEquals(Tap.Day(wednesday), tapOf(day))
    }

    @Test
    fun `an entry with a UID opens that entry on its day`() {
        val entries = row(2).findViewById<ViewGroup>(R.id.entries)
        val parade = entries.getChildAt(2).findViewById<View>(R.id.entry)
        assertEquals("17:00–18:30 Martinsumzug Kita", parade.contentDescription)
        val uid = checkNotNull(SampleData.entries.single { it.title == "Martinsumzug Kita" }.uid)
        assertEquals(Tap.Entry(uid, wednesday), tapOf(parade))
    }

    @Test
    fun `an entry without a UID opens its day`() {
        val entries = row(2).findViewById<ViewGroup>(R.id.entries)
        val special = entries.getChildAt(1).findViewById<View>(R.id.entry)
        assertEquals("Martinstag", special.contentDescription)
        assertEquals(Tap.Day(wednesday), tapOf(special))
    }

    @Test
    fun `an entry with an empty or blank UID opens its day`() {
        for (uid in listOf("", " ")) {
            val entry = Probe.timed(wednesday, icon = null).copy(uid = uid)
            val day = WeekLayout.build(listOf(wednesday), listOf(entry), wednesday).single()
            assertEquals(Tap.Day(wednesday), WeekWidget.tap(day.entries.single(), wednesday))
        }
        val entry = Probe.timed(wednesday, icon = null).copy(uid = "abc")
        val day = WeekLayout.build(listOf(wednesday), listOf(entry), wednesday).single()
        assertEquals(Tap.Entry("abc", wednesday), WeekWidget.tap(day.entries.single(), wednesday))
    }

    @Test
    fun `each entry's tap area lies over its line`() {
        val row = row(5)
        val entries = row.findViewById<ViewGroup>(R.id.entries)
        assertEquals(8, entries.childCount)
        val scale = Scale(application.resources.displayMetrics.density)
        val first = entries.getChildAt(0)
        // Below the divider and the row's padding, from the entries' column on.
        assertEquals(scale.hairline + scale.px(5f), (entries.top + first.top).toFloat(), 1f)
        assertEquals(scale.px(PhoneMonth.CARD_SIDE + 44f + 8f), (entries.left + first.left).toFloat(), 1f)
        assertEquals(scale.px(21f), entries.getChildAt(1).top.toFloat() - first.top, 1f)
    }

    @Test
    fun `narrower than 4 cells the rows stack, and each timed entry's tap area spans its two lines`() {
        val scale = Scale(application.resources.displayMetrics.density)
        // Saturday's entries are all timed: stacked, each takes its time's line above its title's.
        val oneLine = scale.px(TileWeek.LINE + TileWeek.LINE_GAP)
        val twoLines = scale.px(DayRowRenderer.TIME_LINE + TileWeek.LINE + TileWeek.LINE_GAP)
        // 203 dp is 3 cells by Android's table, 276 dp 4; 250 dp is its least width for 4 cells (70 × 4 − 30).
        for ((widthDp, height) in listOf(203 to twoLines, 249 to twoLines, 250 to oneLine, 276 to oneLine)) {
            val list = WidgetHost.applied(application, widthDp, week).findViewById<ListView>(R.id.list)
            val entries = list.adapter.getView(5, null, list).findViewById<ViewGroup>(R.id.entries)
            assertEquals(8, entries.childCount)
            for (index in 0 until entries.childCount) {
                val slot = entries.getChildAt(index).layoutParams.height
                assertEquals("$widthDp dp, entry $index", height, slot.toFloat(), 1f)
            }
        }
    }

    @Test
    fun `it is added 4 cells wide and can be made 150 dp wide and 2 cells high`() {
        val info = { attribute: Int -> WidgetHost.providerAttribute(application, R.xml.widget_week_info, attribute) }
        val scale = Scale(application.resources.displayMetrics.density)
        assertEquals(scale.px(250f).roundToInt(), info(android.R.attr.minWidth))
        assertEquals(scale.px(150f).roundToInt(), info(android.R.attr.minResizeWidth))
        assertEquals(scale.px(180f).roundToInt(), info(android.R.attr.minHeight))
        assertEquals(scale.px(110f).roundToInt(), info(android.R.attr.minResizeHeight))
        assertEquals(4, info(android.R.attr.targetCellWidth))
        assertEquals(4, info(android.R.attr.targetCellHeight))
    }

    @Test
    fun `a row filled anew for another day holds only that day's entry areas`() {
        // On each update, and for a row its list recycles, a launcher applies a row's views again to the views it
        // shows; entry areas added there before must not pile up, or the row grows by blank lines below its drawing.
        val assets = RenderAssets.of(application)
        val rows = DayRowRenderer(assets.fonts, assets.icons, assets.drawings, WeekTexts.of(application.resources))
        val scale = Scale(application.resources.displayMetrics.density)
        val week = WeekLayout.build(WeekDays.MONDAY_TO_SUNDAY.dates(wednesday), SampleData.entries, wednesday)
        val views = { index: Int ->
            val drawn = rows.render(week[index], ROW_PX, scale, SampleData.light)
            WeekWidget.row(application, rows, week[index], drawn, drawn, scale)
        }
        val row = views(5).apply(application, FrameLayout(application))
        val entries = row.findViewById<ViewGroup>(R.id.entries)
        assertEquals(8, entries.childCount)
        views(5).reapply(application, row)
        assertEquals(8, entries.childCount)
        views(1).reapply(application, row)
        assertEquals(listOf("07:45–08:15 Elterngespräch Schule", "15:30–16:15 Zahnarzt Ben"), entries.descriptions())
        views(6).reapply(application, row)
        assertEquals(0, entries.childCount)
    }

    private fun ViewGroup.descriptions() =
        (0 until childCount).map { getChildAt(it).findViewById<View>(R.id.entry).contentDescription }

    @Test
    fun `the list opens scrolled to today`() {
        val list = WidgetHost.applied(application, views = week).findViewById<ListView>(R.id.list)
        assertEquals(2, shadowOf(list).smoothScrolledPosition)
    }

    @Test
    fun `the header and plus send their tap to the tap activity, plus with today`() {
        val widget = WidgetHost.applied(application, views = week)
        assertEquals(Tap.Header, tapOf(widget.findViewById(R.id.header)))
        assertEquals(Tap.New(wednesday), tapOf(widget.findViewById(R.id.fab)))
    }

    @Test
    fun `outside night mode the light drawings show`() {
        assertModes(light = View.VISIBLE, dark = View.GONE)
    }

    @Test
    @Config(qualifiers = "+night")
    fun `in night mode the dark drawings show`() {
        assertModes(light = View.GONE, dark = View.VISIBLE)
    }

    private fun assertModes(
        light: Int,
        dark: Int,
    ) {
        val widget = WidgetHost.applied(application, views = week)
        assertEquals(light, widget.findViewById<View>(R.id.header_light).visibility)
        assertEquals(dark, widget.findViewById<View>(R.id.header_dark).visibility)
        assertEquals(light, widget.findViewById<View>(R.id.fab_light).visibility)
        assertEquals(dark, widget.findViewById<View>(R.id.fab_dark).visibility)
        val list = widget.findViewById<ListView>(R.id.list)
        val row = list.adapter.getView(0, null, list)
        assertEquals(light, row.findViewById<View>(R.id.day_light).visibility)
        assertEquals(dark, row.findViewById<View>(R.id.day_dark).visibility)
    }

    @Test
    fun `an update draws the widget`() {
        val id = shadowOf(manager).createWidget(WeekWidgetProvider::class.java, R.layout.widget_week)
        val widget = shadowOf(manager).getViewFor(id)
        for (image in listOf(R.id.header_light, R.id.header_dark, R.id.fab_light, R.id.fab_dark)) {
            assertTrue(widget.findViewById<ImageView>(image).drawable is BitmapDrawable)
        }
        val log = ShadowLog.getLogsForTag("WeekWidget").single().msg
        assertTrue(log, log.startsWith("Widget $id: drew 18 bitmaps at "))
    }

    @Test
    fun `a size change draws the widget at the new width`() {
        val id = shadowOf(manager).createWidget(WeekWidgetProvider::class.java, R.layout.widget_week)
        val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 203) }
        manager.updateAppWidgetOptions(id, options)
        WeekWidgetProvider().onAppWidgetOptionsChanged(application, manager, id, options)
        val header = shadowOf(manager).getViewFor(id).findViewById<ImageView>(R.id.header_light)
        val scale = Scale(application.resources.displayMetrics.density)
        assertEquals(
            scale.px(203 - 2 * PhoneMonth.CARD_INSET).roundToInt(),
            (header.drawable as BitmapDrawable).bitmap.width,
        )
    }

    @Test
    fun `only the system may send the widget's broadcasts`() {
        val receiver =
            application.packageManager.getReceiverInfo(ComponentName(application, WeekWidgetProvider::class.java), 0)
        assertTrue(receiver.exported)
        assertEquals("android.permission.BIND_APPWIDGET", receiver.permission)
        assertEquals("Woche", receiver.loadLabel(application.packageManager).toString())
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    fun `the layout's measures fit the drawn parts`() {
        val scale = Scale(application.resources.displayMetrics.density)
        val px = { dp: Float -> scale.px(dp).roundToInt() }
        val widget = WidgetHost.laidOut(application, views = week)
        val header = widget.findViewById<View>(R.id.header)
        assertEquals(px(PhoneMonth.CARD_INSET), header.left + (header.parent as View).left)
        assertEquals(px(PhoneMonth.CARD_BORDER + PhoneMonth.CARD_END), header.top + (header.parent as View).top)
        val headerBitmap = (widget.findViewById<ImageView>(R.id.header_light).drawable as BitmapDrawable).bitmap
        assertEquals(header.width, headerBitmap.width)
        val list = widget.findViewById<ListView>(R.id.list)
        assertEquals(px(PhoneMonth.CARD_BORDER), list.left + (list.parent as View).left)
        val day = list.getChildAt(0).findViewById<ImageView>(R.id.day_light)
        val bitmap = (day.drawable as BitmapDrawable).bitmap
        // Drawn at the list's width and the display's density, so the row is shown unscaled.
        assertEquals(list.width, bitmap.width)
        assertEquals(day.width, bitmap.width)
        assertEquals(day.height, bitmap.height)
        val fab = widget.findViewById<View>(R.id.fab)
        assertEquals(px(PhoneMonth.FAB_MARGIN), widget.width - fab.right)
        assertEquals(px(PhoneMonth.FAB_MARGIN), widget.height - fab.bottom)
    }
}
