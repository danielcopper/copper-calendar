package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.store.CardOpacity
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.week.WeekDays
import org.junit.rules.ExternalResource

/**
 * The widgets drawn from the sample month, as the tests and the widget
 * picker's previews draw them (D55), within the display's limit; the
 * widget's id only names it in the log.
 */
internal fun MonthWidget.views(
    context: Context,
    widthDp: Int,
    id: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    calendar: Content.Calendar = SampleData.calendar(),
    corner: PlusCorner = PlusCorner.BOTTOM_RIGHT,
    taps: TapTargets = TapTargets.DEFAULT,
    opacity: Int = CardOpacity.FULL,
): RemoteViews =
    draw(
        context,
        widthDp,
        id,
        BitmapLimit.limitBytes(context, TAG, id),
        calendar,
        look = Look(corner = corner, taps = taps, opacity = opacity),
    ).views

internal fun WeekWidget.views(
    context: Context,
    widthDp: Int,
    days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
    id: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    calendar: Content.Calendar = SampleData.calendar(),
    corner: PlusCorner = PlusCorner.BOTTOM_RIGHT,
    taps: TapTargets = TapTargets.DEFAULT,
    opacity: Int = CardOpacity.FULL,
): RemoteViews =
    draw(
        context,
        widthDp,
        Look(days, corner, taps = taps, opacity = opacity),
        id,
        BitmapLimit.limitBytes(context, TAG, id),
        calendar,
    ).views

internal fun WeekColumnsWidget.views(
    context: Context,
    widthDp: Int,
    heightDp: Int = WeekColumnsWidget.DEFAULT_HEIGHT_DP,
    days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
    id: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    calendar: Content.Calendar = SampleData.calendar(),
    corner: PlusCorner = PlusCorner.BOTTOM_RIGHT,
    taps: TapTargets = TapTargets.DEFAULT,
    opacity: Int = CardOpacity.FULL,
): RemoteViews =
    draw(
        context,
        widthDp,
        heightDp,
        Look(days, corner, taps = taps, opacity = opacity),
        id,
        BitmapLimit.limitBytes(context, TAG, id),
        calendar,
    ).views

/**
 * Makes the providers draw the sample month, as if signed in with a fresh
 * snapshot of it, for the tests of their updates; puts back what they read
 * after each test.
 */
class SampleContent : ExternalResource() {
    private lateinit var kept: (Context) -> Content

    override fun before() {
        kept = DrawnWidgetProvider.content
        DrawnWidgetProvider.content = { SampleData.calendar() }
    }

    override fun after() {
        DrawnWidgetProvider.content = kept
    }
}
