package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
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
    internal const val TAG = "WeekWidget"

    /** The plain text it shows when the system refuses its views (D40). */
    internal val TOO_FULL = R.string.widget_week_too_full

    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        val options = manager.getAppWidgetOptions(id)
        val width = DrawnWidget.widthDp(options, TAG, id)
        val height = TodayOnTop.portraitHeight(options, TAG, id)
        val limit = BitmapLimit.limitBytes(context, TAG, id)
        val drawn = draw(context, width, WeekDays.MONDAY_TO_SUNDAY, id, limit, height = height)
        DrawnWidget.push(context, id, drawn, TOO_FULL, TAG) { manager.updateAppWidget(id, it) }
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
    ): RemoteViews = draw(context, widthDp, days, id, BitmapLimit.limitBytes(context, TAG, id)).views

    /**
     * The widget at widthDp and the bytes of its bitmaps, kept within limit:
     * when its rows would pass it, the fullest days leave out their last
     * entries for a "+N weitere" line until they fit ([leaveOutOne]). Its
     * list scrolls so today stands on top in a widget of height
     * ([TodayOnTop.scroll]).
     */
    internal fun draw(
        context: Context,
        widthDp: Int,
        days: WeekDays,
        id: Int,
        limit: Long,
        entries: List<Entry> = SampleData.entries,
        height: WidgetHeight = DrawnWidget.defaultHeight,
    ): Drawn {
        val today = LocalDate.now(SampleData.clock)
        val dates = days.dates(today)
        val scale = Scale(context.resources.displayMetrics.density)
        val headerPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        // The rows span the card inside its border; they draw inside its side padding themselves.
        val rowPx = scale.px(widthDp - 2 * PhoneMonth.CARD_BORDER).roundToInt()
        val assets = RenderAssets.of(context)
        val texts = WeekTexts.of(context.resources)
        val rows = DayRowRenderer.forWidth(assets.fonts, assets.icons, assets.drawings, texts, widthDp)
        val kept = Kept()
        val title = context.getString(days.title)
        val range = WeekLayout.range(dates.first(), dates.last())
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        var headerHeightPx = 0
        DrawnWidget.fixedParts(context, views, kept, "$title, $range", today, scale) { chrome, palette ->
            chrome.weekHeader(title, range, null, headerPx, scale, palette).also { headerHeightPx = it.height }
        }
        val full = WeekLayout.build(dates, entries, today)
        val bytesOf = { days: List<WeekDay> -> days.sumOf { BitmapLimit.bytes(rowPx, rows.heightPx(it, scale)) } }
        val capped = BitmapLimit.capped(full, limit - kept.bytes, bytesOf, ::leaveOutOne)
        val week = capped.drawn
        val fixed = kept.bytes
        BitmapLimit.warnOver(
            TAG,
            id,
            needed = fixed + bytesOf(full),
            limit = limit,
            left = capped.left,
            after = fixed + bytesOf(week),
        )
        val heights = mutableListOf<Int>()
        val items =
            week.map { day ->
                val light = kept.keep(rows.render(day, rowPx, scale, SampleData.light))
                val dark = kept.keep(rows.render(day, rowPx, scale, SampleData.dark))
                heights += light.height
                day.date.toEpochDay() to row(context, rows, day, light, dark, scale)
            }
        DrawnWidget.list(context, views, items)
        val listPx = DrawnWidget.listPx(scale, height.dp, headerHeightPx)
        val scrolled = TodayOnTop.scroll(context, views, heights, week.indexOfFirst { it.today }, listPx, height)
        DrawnWidget.logDrawn(TAG, id, kept, rowPx, limit, scrolled)
        return kept.drawn(views, limit)
    }

    /**
     * The days with one more entry left out, counted in its "+N weitere"
     * line: from the day showing the most, the first of them when several
     * do; none when no day shows one.
     */
    internal fun leaveOutOne(days: List<WeekDay>): List<WeekDay>? {
        val fullest = days.indices.filter { days[it].shown.isNotEmpty() }.maxByOrNull { days[it].shown.size }
        return fullest?.let { index -> days.mapIndexed { i, day -> if (i == index) day.leaveOutLast() else day } }
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

    /** What a tap on an entry opens ([Tap.entryOrDay]). */
    internal fun tap(
        entry: DayEntry,
        date: LocalDate,
    ): Tap = Tap.entryOrDay(entry.entry.uid, date)
}
