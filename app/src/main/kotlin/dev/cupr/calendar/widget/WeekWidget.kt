package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.DayRowRenderer
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekTexts
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import dev.cupr.calendar.week.DayEntry
import dev.cupr.calendar.week.WeekDay
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The week widget "Woche" (D14, D19), after the dashboard's tile: the month
 * widget's header and "+", and a list of drawn day rows, each the day's tap
 * area with a tap area per entry over it, stacked when the widget is
 * narrower than 4 cells ([DayRowRenderer.stacksAt]). Each drawn part comes
 * twice, light and dark, as in [MonthWidget].
 */
object WeekWidget {
    private const val TAG = "WeekWidget"
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
                .takeIf { it > 0 } ?: MonthWidget.DEFAULT_WIDTH_DP
        manager.updateAppWidget(id, views(context, width, id = id))
    }

    /**
     * The widget at widthDp showing days, for the sample entries while the
     * app has no other data: its "today" is the sample's
     * ([SampleData.clock]). The widget's id only names it in the log.
     */
    fun views(
        context: Context,
        widthDp: Int,
        days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
        id: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
    ): RemoteViews {
        val today = LocalDate.now(SampleData.clock)
        val dates = days.dates(today)
        val week = WeekLayout.build(dates, SampleData.entries, today)
        val scale = Scale(context.resources.displayMetrics.density)
        val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        // The rows span the card inside its border; they draw inside its side padding themselves.
        val rowPx = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
        val assets = RenderAssets.of(context)
        val chrome =
            ChromeRenderer(assets.fonts, assets.icons, context.resources.getStringArray(R.array.weekday_heads).toList())
        val rows =
            DayRowRenderer.forWidth(assets.fonts, assets.icons, assets.drawings, WeekTexts.of(context.resources), widthDp)
        val drawn = mutableListOf<Bitmap>()

        fun Bitmap.kept(): Bitmap = also { drawn += it }

        val title = context.getString(days.title)
        val range = WeekLayout.range(dates.first(), dates.last())
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        for ((image, palette) in listOf(R.id.header_light to SampleData.light, R.id.header_dark to SampleData.dark)) {
            views.setImageViewBitmap(image, chrome.weekHeader(title, range, null, headerPx, scale, palette).kept())
        }
        views.setContentDescription(R.id.header, "$title, $range")
        views.setOnClickPendingIntent(R.id.header, Taps.pendingIntent(context, Tap.Header))
        views.setImageViewBitmap(R.id.fab_light, chrome.fab(scale, SampleData.light).kept())
        views.setImageViewBitmap(R.id.fab_dark, chrome.fab(scale, SampleData.dark).kept())
        views.setOnClickPendingIntent(R.id.fab, Taps.pendingIntent(context, Tap.New(today)))

        val items =
            RemoteViews.RemoteCollectionItems
                .Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
        for (day in week) {
            val light = rows.render(day, rowPx, scale, SampleData.light).kept()
            val dark = rows.render(day, rowPx, scale, SampleData.dark).kept()
            items.addItem(day.date.toEpochDay(), row(context, rows, day, light, dark, scale))
        }
        views.setRemoteAdapter(R.id.list, items.build())
        views.setPendingIntentTemplate(R.id.list, Taps.template(context))
        week.indexOfFirst { it.today }.takeIf { it >= 0 }?.let { views.setScrollPosition(R.id.list, it) }
        Log.i(
            TAG,
            "Widget $id: drew ${drawn.size} bitmaps at ${rowPx}px wide, " +
                "${drawn.sumOf { it.allocationByteCount }} bytes",
        )
        return views
    }

    /**
     * A day's row: the drawn row as the day's tap area, and over each entry's
     * line a tap area of its own, which opens the entry by its UID, or its
     * day when it has none (D37). Internal for the test that applies it again
     * as a launcher does.
     */
    internal fun row(
        context: Context,
        rows: DayRowRenderer,
        day: WeekDay,
        light: Bitmap,
        dark: Bitmap,
        scale: Scale,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_week_day)
        row.setImageViewBitmap(R.id.day_light, light)
        row.setImageViewBitmap(R.id.day_dark, dark)
        row.setOnClickFillInIntent(R.id.day_row, Taps.fillIn(Tap.Day(day.date)))
        row.setContentDescription(R.id.day_row, day.date.format(dayLabel))
        // A launcher applies a row's views again to the views it shows (on each update, and to a row its list
        // recycles); views added before would stay and pile up, growing the row by blank lines below its drawing.
        row.removeAllViews(R.id.entries)
        val areas = rows.entryAreas(day, light.width, scale)
        check(areas.size == day.shown.size) { "${areas.size} tap areas for ${day.shown.size} entries on ${day.date}" }
        val first = areas.firstOrNull() ?: return row
        row.setViewPadding(
            R.id.entries,
            first.left.roundToInt(),
            first.top.roundToInt(),
            (light.width - first.right).roundToInt(),
            0,
        )
        day.shown.zip(areas).forEach { (entry, area) ->
            val cell = RemoteViews(context.packageName, R.layout.widget_week_entry)
            // Each slot ends where the area ends, rounded, so rounding never adds up down the row.
            val height = area.bottom.roundToInt() - area.top.roundToInt()
            cell.setViewLayoutHeight(R.id.entry_slot, height.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            cell.setOnClickFillInIntent(R.id.entry, Taps.fillIn(tap(entry, day.date)))
            cell.setContentDescription(R.id.entry, rows.describe(entry))
            row.addView(R.id.entries, cell)
        }
        return row
    }

    /** What a tap on an entry opens: the entry by its UID on date, or date when it has none or a blank one (D37). */
    internal fun tap(
        entry: DayEntry,
        date: LocalDate,
    ): Tap =
        entry.entry.uid
            ?.takeIf { it.isNotBlank() }
            ?.let { Tap.Entry(it, date) } ?: Tap.Day(date)
}
