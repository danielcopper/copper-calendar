package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekRowRenderer
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The week widget "Woche quer" (D19, D35): the month widget's header with the
 * week's title, range and weekday heads, its "+", and a list of one drawn
 * row, the seven days as a week of the month with every entry of a day, so
 * the list scrolls when a day is fuller than the widget is tall. A fixed fade
 * above the list's bottom edge shows then, and the row ends in blank room as
 * tall as the fade, so its last entries come out from under it at the end of
 * the scroll. Each drawn part comes twice, light and dark, as in
 * [MonthWidget].
 */
object WeekColumnsWidget {
    /** The widget's height before the launcher reports one: 2 cells (design.md). */
    internal const val DEFAULT_HEIGHT_DP = 220

    /** The fade's height, as widget_week_columns.xml has it. */
    internal const val FADE_DP = 24
    private const val TAG = "WeekColumnsWidget"

    private val dayIds =
        intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
    private val dayLabel = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        // In portrait, the orientation the phones use, a widget is as wide as its least width and as tall as its
        // greatest height.
        val options = manager.getAppWidgetOptions(id)
        val width =
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }
                ?: MonthWidget.DEFAULT_WIDTH_DP
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
        manager.updateAppWidget(id, views(context, width, height, id = id))
    }

    /**
     * The widget at widthDp × heightDp showing days, for the sample entries
     * while the app has no other data: its "today" is the sample's
     * ([SampleData.clock]). The widget's id only names it in the log.
     */
    fun views(
        context: Context,
        widthDp: Int,
        heightDp: Int = DEFAULT_HEIGHT_DP,
        days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
        id: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val today = LocalDate.now(SampleData.clock)
        val dates = days.dates(today)
        val week = MonthLayout.week(dates, SampleData.entries, today)
        val scale = Scale(context.resources.displayMetrics.density)
        // The drawn row and header span the widget less the card's inset at both sides.
        val widthPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val assets = RenderAssets.of(context)
        val chrome =
            ChromeRenderer(assets.fonts, assets.icons, context.resources.getStringArray(R.array.weekday_heads).toList())
        val rows = WeekRowRenderer(assets.fonts, assets.icons, assets.drawings)
        val drawn = mutableListOf<Bitmap>()

        fun Bitmap.kept(): Bitmap = also { drawn += it }

        val title = context.getString(days.title)
        val range = WeekLayout.range(dates.first(), dates.last())
        val views = RemoteViews(context.packageName, R.layout.widget_week_columns)
        val headers =
            listOf(SampleData.light, SampleData.dark).map {
                chrome.weekHeader(title, range, dates.first().dayOfWeek, widthPx, scale, it).kept()
            }
        views.setImageViewBitmap(R.id.header_light, headers[0])
        views.setImageViewBitmap(R.id.header_dark, headers[1])
        views.setContentDescription(R.id.header, "$title, $range")
        views.setOnClickPendingIntent(R.id.header, Taps.pendingIntent(context, Tap.Header))
        views.setImageViewBitmap(R.id.fab_light, chrome.fab(scale, SampleData.light).kept())
        views.setImageViewBitmap(R.id.fab_dark, chrome.fab(scale, SampleData.dark).kept())
        views.setOnClickPendingIntent(R.id.fab, Taps.pendingIntent(context, Tap.New(today)))

        val faded = overflows(rows, week, scale, heightDp, headers[0].height)
        val blank = if (faded) FADE_DP.toFloat() else 0f
        views.setViewVisibility(R.id.fade, if (faded) View.VISIBLE else View.GONE)
        val light = rows.render(week, widthPx, scale, SampleData.light, blank).kept()
        val dark = rows.render(week, widthPx, scale, SampleData.dark, blank).kept()
        val items =
            RemoteViews.RemoteCollectionItems
                .Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
                .addItem(dates.first().toEpochDay(), row(context, week, light, dark))
        views.setRemoteAdapter(R.id.list, items.build())
        views.setPendingIntentTemplate(R.id.list, Taps.template(context))
        Log.i(
            TAG,
            "Widget $id: drew ${drawn.size} bitmaps at ${widthPx}px wide, " +
                "${drawn.sumOf { it.allocationByteCount }} bytes" + if (faded) ", faded" else "",
        )
        return views
    }

    /**
     * Whether the drawn row is taller than the list shows at heightDp: the
     * widget less the card's border and padding at the top and bottom, and
     * less the header, headerPx high.
     */
    internal fun overflows(
        rows: WeekRowRenderer,
        week: Week,
        scale: Scale,
        heightDp: Int,
        headerPx: Int,
    ): Boolean {
        val rowPx = ceil(scale.hairline + scale.px(rows.heightDp(week)))
        val listPx = scale.px(heightDp - 2 * (PhoneMonth.CARD_BORDER + PhoneMonth.CARD_END)) - headerPx
        return rowPx > listPx
    }

    /** The drawn row and over it one tap area per day (widget_month_week.xml, the month's week row). */
    private fun row(
        context: Context,
        week: Week,
        light: Bitmap,
        dark: Bitmap,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_month_week)
        row.setImageViewBitmap(R.id.week_light, light)
        row.setImageViewBitmap(R.id.week_dark, dark)
        week.days.forEachIndexed { index, day ->
            row.setOnClickFillInIntent(dayIds[index], Taps.fillIn(Tap.Day(day.date)))
            row.setContentDescription(dayIds[index], day.date.format(dayLabel))
        }
        return row
    }
}
