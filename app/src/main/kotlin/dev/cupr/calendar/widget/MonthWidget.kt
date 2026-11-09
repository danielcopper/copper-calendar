package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.Drawings
import dev.cupr.calendar.render.Fonts
import dev.cupr.calendar.render.Icons
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekRowRenderer
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.TapElement
import dev.cupr.calendar.tap.Taps
import java.time.LocalDateTime
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
    private const val DEFAULT_WIDTH_DP = 276

    private val dayIds =
        intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
    private val dayLabel = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

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
        manager.updateAppWidget(id, views(context, width))
    }

    /**
     * The widget at widthDp, for the sample month while the app has no
     * other data: its "today" is the sample's ([SampleData.clock]).
     */
    fun views(
        context: Context,
        widthDp: Int,
    ): RemoteViews {
        val now = LocalDateTime.now(SampleData.clock)
        val today = now.toLocalDate()
        val view = MonthLayout.build(YearMonth.from(today), SampleData.entries, now)
        val scale = Scale(context.resources.displayMetrics.density)
        val widthPx = scale.px(widthDp - 2 * (PhoneMonth.CARD_BORDER + PhoneMonth.CARD_SIDE)).roundToInt()
        val fonts = Fonts(context.assets)
        val icons = Icons(context.assets)
        val chrome = ChromeRenderer(fonts, icons)
        val rows = WeekRowRenderer(fonts, icons, Drawings(context.assets))

        val views = RemoteViews(context.packageName, R.layout.widget_month)
        views.setImageViewBitmap(R.id.header_light, chrome.header(view.month, widthPx, scale, SampleData.light))
        views.setImageViewBitmap(R.id.header_dark, chrome.header(view.month, widthPx, scale, SampleData.dark))
        views.setContentDescription(R.id.header, ChromeRenderer.title(view.month))
        views.setOnClickPendingIntent(R.id.header, Taps.pendingIntent(context, TapElement.HEADER, today))
        views.setImageViewBitmap(R.id.fab_light, chrome.fab(scale, SampleData.light))
        views.setImageViewBitmap(R.id.fab_dark, chrome.fab(scale, SampleData.dark))
        views.setOnClickPendingIntent(R.id.fab, Taps.pendingIntent(context, TapElement.NEW, today))

        val items =
            RemoteViews.RemoteCollectionItems
                .Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
        for (week in view.weeks) {
            val light = rows.render(week, widthPx, scale, SampleData.light)
            val dark = rows.render(week, widthPx, scale, SampleData.dark)
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
            row.setOnClickFillInIntent(dayIds[index], Taps.fillIn(TapElement.DAY, day.date))
            row.setContentDescription(dayIds[index], day.date.format(dayLabel))
        }
        return row
    }
}
