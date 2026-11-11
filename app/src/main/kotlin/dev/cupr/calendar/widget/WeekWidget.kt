package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.days.longName
import dev.cupr.calendar.model.Entry
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
import kotlin.math.roundToInt

/**
 * The week widget "Woche" (D14, D19), after the dashboard's tile: the month
 * widget's header and "+", and a list of drawn day rows, each the day's tap
 * area with a tap area per entry over it, stacked when the widget is
 * narrower than 4 cells ([DayRowRenderer.forWidth]). Each drawn part comes
 * twice, light and dark, as in [MonthWidget].
 */
object WeekWidget {
    private const val TAG = "WeekWidget"

    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        val width = DrawnWidget.widthDp(manager.getAppWidgetOptions(id), TAG, id)
        val drawn = draw(context, width, WeekDays.MONDAY_TO_SUNDAY, id, DrawnWidget.limitBytes(context))
        DrawnWidget.push(context, id, drawn, R.string.widget_week_too_full, TAG) { manager.updateAppWidget(id, it) }
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
    ): RemoteViews = draw(context, widthDp, days, id, DrawnWidget.limitBytes(context)).views

    /**
     * The widget at widthDp and the bytes of its bitmaps, kept within limit:
     * when its rows would pass it, the fullest days leave out their last
     * entries for a "+N weitere" line until they fit ([capped]).
     */
    internal fun draw(
        context: Context,
        widthDp: Int,
        days: WeekDays,
        id: Int,
        limit: Long,
        entries: List<Entry> = SampleData.entries,
    ): Drawn {
        val today = LocalDate.now(SampleData.clock)
        val dates = days.dates(today)
        val scale = Scale(context.resources.displayMetrics.density)
        val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        // The rows span the card inside its border; they draw inside its side padding themselves.
        val rowPx = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
        val assets = RenderAssets.of(context)
        val chrome = DrawnWidget.chrome(context)
        val texts = WeekTexts.of(context.resources)
        val rows = DayRowRenderer.forWidth(assets.fonts, assets.icons, assets.drawings, texts, widthDp)
        val kept = Kept()
        val title = context.getString(days.title)
        val range = WeekLayout.range(dates.first(), dates.last())
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        DrawnWidget.fixedParts(context, views, kept, chrome, "$title, $range", today, scale) {
            chrome.weekHeader(title, range, null, headerPx, scale, it)
        }
        val full = WeekLayout.build(dates, entries, today)
        val week = capped(full, limit - kept.bytes) { DrawnWidget.bytes(rowPx, rows.heightPx(it, scale)) }
        val left = week.sumOf { it.more }
        if (left > 0) {
            val needed = kept.bytes + full.sumOf { DrawnWidget.bytes(rowPx, rows.heightPx(it, scale)) }
            Log.w(TAG, "Widget $id: its rows take $needed bytes, over the limit of $limit; $left entries left out")
        }
        val items =
            week.map { day ->
                val light = kept.keep(rows.render(day, rowPx, scale, SampleData.light))
                val dark = kept.keep(rows.render(day, rowPx, scale, SampleData.dark))
                day.date.toEpochDay() to row(context, rows, day, light, dark, scale)
            }
        DrawnWidget.list(context, views, items)
        week.indexOfFirst { it.today }.takeIf { it >= 0 }?.let { views.setScrollPosition(R.id.list, it) }
        DrawnWidget.logDrawn(TAG, id, kept, rowPx, limit)
        return Drawn(views, kept.bytes, limit)
    }

    /**
     * The days within room bytes, as bytesOf counts a day's row: while they
     * are over it, the day showing the most entries leaves out its last one
     * more, counted in its "+N weitere" line; days with none left stay as they
     * are, and so may the whole when even that does not fit.
     */
    internal fun capped(
        days: List<WeekDay>,
        room: Long,
        bytesOf: (WeekDay) -> Long,
    ): List<WeekDay> {
        val week = days.toMutableList()
        while (week.sumOf(bytesOf) > room) {
            val fullest =
                week.indices.filter { week[it].shown.isNotEmpty() }.maxByOrNull { week[it].shown.size } ?: break
            week[fullest] = week[fullest].copy(more = week[fullest].more + 1)
        }
        return week
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
        row.setContentDescription(R.id.day_row, day.date.longName)
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
