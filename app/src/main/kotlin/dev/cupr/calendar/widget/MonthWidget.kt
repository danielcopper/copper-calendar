package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.month.DAYS_IN_WEEK
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The month widget's views (D8, D23): a fixed header, a list of drawn week
 * rows with a tap area per day, and a fixed "+" above the list. Each drawn
 * part comes twice, light and dark; the layouts show one of the two by
 * night-mode resources (`values-night/integers.xml`), so the launcher
 * switches the mode without the app's process.
 */
object MonthWidget {
    /** The widget's width before the launcher reports one: 4 cells (design.md). */
    internal const val DEFAULT_WIDTH_DP = 276
    private const val TAG = "MonthWidget"

    private val dayIds =
        intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
    private val dayLabel = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    init {
        check(dayIds.size == DAYS_IN_WEEK)
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        // In portrait, the orientation the phones use, a widget is as wide as its least width.
        val width =
            manager
                .getAppWidgetOptions(id)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                .takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
        manager.updateAppWidget(id, views(context, width, id))
    }

    /**
     * The widget at widthDp, for the sample month while the app has no
     * other data: its "today" is the sample's ([SampleData.clock]). The
     * widget's id only names it in the log.
     */
    fun views(
        context: Context,
        widthDp: Int,
        id: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val today = LocalDate.now(SampleData.clock)
        val view = MonthLayout.build(YearMonth.from(today), SampleData.entries, today)
        val scale = Scale(context.resources.displayMetrics.density)
        // The drawn rows and header span the widget less the card's inset at both sides.
        val widthPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val assets = RenderAssets.of(context)
        val chrome =
            ChromeRenderer(assets.fonts, assets.icons, context.resources.getStringArray(R.array.weekday_heads).toList())
        val rows = WeekRowRenderer(assets.fonts, assets.icons, assets.drawings)
        val drawn = mutableListOf<Bitmap>()

        fun Bitmap.kept(): Bitmap = also { drawn += it }

        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.setImageViewBitmap(R.id.header_light, chrome.header(view.month, widthPx, scale, SampleData.light).kept())
        views.setImageViewBitmap(R.id.header_dark, chrome.header(view.month, widthPx, scale, SampleData.dark).kept())
        views.setContentDescription(R.id.header, ChromeRenderer.title(view.month))
        views.setOnClickPendingIntent(R.id.header, Taps.pendingIntent(context, Tap.Header))
        views.setImageViewBitmap(R.id.fab_light, chrome.fab(scale, SampleData.light).kept())
        views.setImageViewBitmap(R.id.fab_dark, chrome.fab(scale, SampleData.dark).kept())
        views.setOnClickPendingIntent(R.id.fab, Taps.pendingIntent(context, Tap.New(today)))

        val items =
            RemoteViews.RemoteCollectionItems
                .Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
        for (week in view.weeks) {
            val light = rows.render(week, widthPx, scale, SampleData.light).kept()
            val dark = rows.render(week, widthPx, scale, SampleData.dark).kept()
            items.addItem(
                week.days
                    .first()
                    .date
                    .toEpochDay(),
                row(context, week, light, dark),
            )
        }
        views.setRemoteAdapter(R.id.list, items.build())
        views.setPendingIntentTemplate(R.id.list, Taps.template(context))
        views.setScrollPosition(R.id.list, view.todayWeek)
        Log.i(
            TAG,
            "Widget $id: drew ${drawn.size} bitmaps at ${widthPx}px wide, " +
                "${drawn.sumOf { it.allocationByteCount }} bytes",
        )
        return views
    }

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
