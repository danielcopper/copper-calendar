package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.model.Entry
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekRowRenderer
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.week.WeekDays
import dev.cupr.calendar.week.WeekLayout
import java.time.LocalDate
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

    /**
     * Draws the widget at its width and its least height: in portrait it is
     * taller, so the fade shows whenever the row is taller than the list at
     * the least height, also when portrait's list would hold it.
     */
    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        val options = manager.getAppWidgetOptions(id)
        val width = DrawnWidget.widthDp(options, TAG, id)
        val height =
            DrawnWidget.option(
                options,
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                DEFAULT_HEIGHT_DP,
                TAG,
                id,
            )
        val drawn = draw(context, width, height, WeekDays.MONDAY_TO_SUNDAY, id, DrawnWidget.limitBytes(context))
        DrawnWidget.push(context, id, drawn, R.string.widget_week_too_full, TAG) { manager.updateAppWidget(id, it) }
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
    ): RemoteViews = draw(context, widthDp, heightDp, days, id, DrawnWidget.limitBytes(context)).views

    /**
     * The widget at widthDp × heightDp and the bytes of its bitmaps, kept
     * within limit: when its row would pass it, the fullest days leave out
     * their last entries for the month's "+N" until it fits ([capped]).
     */
    internal fun draw(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        days: WeekDays,
        id: Int,
        limit: Long,
        entries: List<Entry> = SampleData.entries,
    ): Drawn {
        val today = LocalDate.now(SampleData.clock)
        val dates = days.dates(today)
        val scale = Scale(context.resources.displayMetrics.density)
        // The drawn row and header span the widget less the card's inset at both sides.
        val widthPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val assets = RenderAssets.of(context)
        val chrome = DrawnWidget.chrome(context)
        val rows = WeekRowRenderer(assets.fonts, assets.icons, assets.drawings)
        val kept = Kept()
        val title = context.getString(days.title)
        val range = WeekLayout.range(dates.first(), dates.last())
        val views = RemoteViews(context.packageName, R.layout.widget_week_columns)
        var headerPx = 0
        DrawnWidget.fixedParts(context, views, kept, chrome, "$title, $range", today, scale) {
            chrome.weekHeader(title, range, dates.first().dayOfWeek, widthPx, scale, it).also { headerPx = it.height }
        }
        val full = MonthLayout.week(dates, entries, today)
        // Room for the fade's blank room counted in, as a row that has to be capped is taller than its list.
        val fadeDp = FADE_DP.toFloat()
        val week = capped(full, limit - kept.bytes) { DrawnWidget.bytes(widthPx, rows.heightPx(it, scale, fadeDp)) }
        val left = week.days.sumOf { it.more }
        if (left > 0) {
            val needed = kept.bytes + DrawnWidget.bytes(widthPx, rows.heightPx(full, scale, fadeDp))
            Log.w(TAG, "Widget $id: its row takes $needed bytes, over the limit of $limit; $left entries left out")
        }
        val faded = overflows(rows, week, scale, heightDp, headerPx)
        val blank = if (faded) fadeDp else 0f
        views.setViewVisibility(R.id.fade, if (faded) View.VISIBLE else View.GONE)
        val light = kept.keep(rows.render(week, widthPx, scale, SampleData.light, blank))
        val dark = kept.keep(rows.render(week, widthPx, scale, SampleData.dark, blank))
        DrawnWidget.list(
            context,
            views,
            listOf(dates.first().toEpochDay() to DrawnWidget.weekRow(context, week, light, dark)),
        )
        DrawnWidget.logDrawn(TAG, id, kept, widthPx, limit, if (faded) ", faded" else "")
        return Drawn(views, kept.bytes, limit)
    }

    /**
     * The week within room bytes, as bytesOf counts its row: while it is over
     * it, the tallest day, by its lines, leaves out its last entry more,
     * counted in its "+N"; when no day has an entry left, it stays as it is.
     */
    internal fun capped(
        week: Week,
        room: Long,
        bytesOf: (Week) -> Long,
    ): Week {
        var capped = week
        while (bytesOf(capped) > room) {
            val days = capped.days
            val tallest =
                days.indices
                    .filter { days[it].shown.isNotEmpty() }
                    .maxByOrNull { days[it].shown.size + if (days[it].more > 0) 1 else 0 } ?: break
            val day = days[tallest]
            val less = day.copy(shown = day.shown.dropLast(1), more = day.more + 1)
            capped = capped.copy(days = days.toMutableList().also { it[tallest] = less })
        }
        return capped
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
        val listPx = scale.px(heightDp - 2 * (PhoneMonth.CARD_BORDER + PhoneMonth.CARD_END)) - headerPx
        return rows.heightPx(week, scale) > listPx
    }
}
