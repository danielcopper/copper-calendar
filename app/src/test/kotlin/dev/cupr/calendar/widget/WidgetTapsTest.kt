package dev.cupr.calendar.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.R
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.signin.SignInActivity
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.TapTarget
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.TapActivity
import dev.cupr.calendar.tap.Taps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The taps of a drawn widget by its targets (D63): which elements have one, and what each sends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTapsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val tuesday = LocalDate.of(2026, 11, 10)
    private val nothing = TapTargets(TapTarget.None, TapTarget.None, TapTarget.None, TapTarget.None)

    @Test
    fun `in woche, elements set to open nothing have no tap, and plus set so is hidden`() {
        val widget = week(nothing)

        assertFalse(widget.findViewById<View>(R.id.header).hasOnClickListeners())
        val rows = rows(widget)
        assertTrue(rows.isNotEmpty())
        for (row in rows) {
            assertFalse(row.findViewById<View>(R.id.day_row).hasOnClickListeners())
            assertTrue(entries(row).none { it.hasOnClickListeners() })
        }
        assertTrue("no entry areas", rows.any { entries(it).isNotEmpty() })
        assertPlusHidden(widget)
    }

    @Test
    fun `in woche, entries set to open nothing have no tap of their own, and their days keep theirs`() {
        val widget = week(TapTargets(entry = TapTarget.None))
        val row = row(widget, TUESDAY)

        assertTrue(entries(row).isNotEmpty())
        assertTrue(entries(row).none { it.hasOnClickListeners() })
        assertEquals(Tap.Day(tuesday), Taps.read(tapped(row.findViewById(R.id.day_row))))
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.plus_right).visibility)
    }

    @Test
    fun `in the month and woche quer, days set to open nothing have no tap, and plus is hidden in either corner`() {
        val taps = TapTargets(day = TapTarget.None, new = TapTarget.None)
        val month = laidOut(WidgetHost.HEIGHT_DP) { c, width -> MonthWidget.views(c, width, ID, taps = taps) }
        val columns =
            laidOut(WidgetHost.LOW_HEIGHT_DP) { c, width ->
                WeekColumnsWidget.views(c, width, id = ID, corner = PlusCorner.BOTTOM_LEFT, taps = taps)
            }

        for (widget in listOf(month, columns)) {
            for (row in rows(widget)) {
                for (day in DAYS) assertFalse(row.findViewById<View>(day).hasOnClickListeners())
            }
            assertEquals(Tap.Header, Taps.read(tapped(widget.findViewById(R.id.header))))
            assertPlusHidden(widget)
        }
    }

    @Test
    fun `each tap of a widget carries its id, so the tap activity reads that widget's targets`() {
        val widget = week(TapTargets.DEFAULT)
        val row = row(widget, TUESDAY)

        val taps =
            listOf(
                widget.findViewById<View>(R.id.header),
                widget.findViewById(R.id.fab),
                row.findViewById(R.id.day_row),
                entries(row).first(),
            ).map(::tapped)

        assertEquals(listOf(ID, ID, ID, ID), taps.map(Taps::widget))
        assertTrue(taps.all { it.component?.className == TapActivity::class.java.name })
    }

    @Test
    fun `while the header asks to sign in again, it opens the sign-in screen whatever its target`() {
        val calendar = SampleData.calendar().copy(notice = Notice.SignInAgain("Neu anmelden"))
        val widget =
            WidgetHost.applied(context) { c, width ->
                MonthWidget
                    .draw(c, width, ID, Long.MAX_VALUE, calendar, look = Look(taps = nothing))
                    .views
            }

        assertEquals(SignInActivity::class.java.name, tapped(widget.findViewById(R.id.header)).component?.className)
    }

    @Test
    fun `a plain text opens the calendar page whatever the widget's targets, so its tap carries no widget`() {
        val views = DrawnWidget.plainViews(context, R.string.widget_failed)
        val text = views.apply(context, null).findViewById<TextView>(R.id.plain_text)

        val tap = tapped(text)

        assertEquals(Tap.Header, Taps.read(tap))
        assertNull(Taps.widget(tap))
    }

    private fun week(taps: TapTargets): View =
        laidOut(WidgetHost.HEIGHT_DP) { c, width -> WeekWidget.views(c, width, id = ID, taps = taps) }

    private fun laidOut(
        heightDp: Int,
        views: (Context, Int) -> RemoteViews,
    ): View = WidgetHost.laidOut(context, heightDp, views)

    private fun rows(widget: View): List<View> {
        val list = widget.findViewById<ListView>(R.id.list)
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    /** The laid-out row of a day of the week (0 = Monday). */
    private fun row(
        widget: View,
        index: Int,
    ): View {
        val list = widget.findViewById<ListView>(R.id.list)
        return checkNotNull(list.getChildAt(index - list.firstVisiblePosition)) { "row $index is not shown" }
    }

    private fun entries(row: View): List<View> {
        val entries = row.findViewById<ViewGroup>(R.id.entries)
        return (0 until entries.childCount).map { entries.getChildAt(it).findViewById(R.id.entry) }
    }

    /** What a tap on view started. */
    private fun tapped(view: View): Intent {
        assertTrue("no tap on $view", view.hasOnClickListeners())
        view.performClick()
        return checkNotNull(shadowOf(context).nextStartedActivity) { "the tap started nothing" }
    }

    private fun assertPlusHidden(widget: View) {
        assertEquals(View.GONE, widget.findViewById<View>(R.id.plus_right).visibility)
        assertEquals(View.GONE, widget.findViewById<View>(R.id.plus_left).visibility)
        assertNotNull(widget.findViewById<View>(R.id.fab))
    }

    private companion object {
        const val ID = 31

        /** A day of the sample week whose entries have UIDs. */
        const val TUESDAY = 1
        val DAYS = listOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
    }
}
