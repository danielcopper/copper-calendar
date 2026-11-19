package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekRowRenderer
import dev.cupr.calendar.week.WeekLayout
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
    internal const val TAG = "WeekColumnsWidget"

    /** The plain text it shows when the system refuses its views (D40). */
    internal val TOO_FULL = R.string.widget_week_too_full

    /**
     * Draws the widget at its width and its least height: in portrait it is
     * taller, so the fade shows whenever the row is taller than the list at
     * the least height, also when portrait's list would hold it.
     */
    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        calendar: Content.Calendar,
        look: Look,
    ): Boolean {
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
        val limit = BitmapLimit.limitBytes(context, TAG, id)
        val drawn = draw(context, width, height, look, id, limit, calendar)
        return DrawnWidget.push(context, id, drawn, TOO_FULL, TAG) { manager.updateAppWidget(id, it) }
    }

    /**
     * The widget at widthDp × heightDp showing look's days of calendar, from
     * its today, and the bytes of its bitmaps, kept within limit: when its
     * row would pass it, the tallest days leave out their last entries for
     * the month's "+N" until it fits ([leaveOutOne]). The widget's id names
     * it in the log and its taps. "+" stands in look's corner, and its taps
     * open what look's say ([Look]); its one row does not scroll to today
     * (D27).
     */
    internal fun draw(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        look: Look,
        id: Int,
        limit: Long,
        calendar: Content.Calendar,
    ): Drawn {
        val today = calendar.today
        val days = look.days
        val dates = days.dates(today)
        val scale = Scale(context.resources.displayMetrics.density)
        // The drawn row and header span the widget less the card's inset at both sides.
        val widthPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val assets = RenderAssets.of(context)
        val rows = WeekRowRenderer(assets.fonts, assets.icons, assets.drawings)
        val kept = Kept()
        val title = context.getString(days.title)
        val range = WeekLayout.range(dates.first(), dates.last())
        val views = RemoteViews(context.packageName, R.layout.widget_week_columns)
        val description = "$title, $range"
        val headerPx =
            DrawnWidget.fixedParts(context, views, kept, description, calendar, look, id) { chrome, palette, note ->
                chrome.weekHeader(title, range, dates.first().dayOfWeek, widthPx, scale, palette, note)
            }
        val full = MonthLayout.week(dates, calendar.entries, today)
        // Room for the fade's blank room counted in, as a row that has to be capped is taller than its list.
        val fadeDp = FADE_DP.toFloat()
        val bytesOf = { week: Week -> BitmapLimit.bytes(widthPx, rows.heightPx(week, scale, fadeDp)) }
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
        DrawnWidget.warnMarks(TAG, id, rows.marksWithoutRoom(week, widthPx, scale))
        val faded = overflows(rows, week, scale, heightDp, headerPx)
        val blank = if (faded) fadeDp else 0f
        views.setViewVisibility(R.id.fade, if (faded) View.VISIBLE else View.GONE)
        val light = kept.keep(rows.render(week, widthPx, scale, calendar.palettes.light, blank))
        val dark = kept.keep(rows.render(week, widthPx, scale, calendar.palettes.dark, blank))
        DrawnWidget.list(
            context,
            views,
            listOf(dates.first().toEpochDay() to DrawnWidget.weekRow(context, week, light, dark, id, look.taps)),
        )
        DrawnWidget.logDrawn(TAG, id, kept, widthPx, limit, if (faded) ", faded" else "")
        return kept.drawn(views, limit)
    }

    /**
     * The week with one more entry left out, counted in its day's "+N": from
     * the day reaching deepest into the lanes ([Week.depthOf]) of those that
     * show one, the first of them when several do; none when no day shows one.
     */
    internal fun leaveOutOne(week: Week): Week? {
        val days = week.days
        val deepest = days.indices.filter { days[it].shown.isNotEmpty() }.maxByOrNull { week.depthOf(it) }
        return deepest?.let { index ->
            week.copy(days = days.mapIndexed { i, day -> if (i == index) day.leaveOutLast() else day })
        }
    }

    /**
     * Whether the drawn row is taller than the list shows at heightDp below
     * the header, headerPx high ([DrawnWidget.listPx]).
     */
    internal fun overflows(
        rows: WeekRowRenderer,
        week: Week,
        scale: Scale,
        heightDp: Int,
        headerPx: Int,
    ): Boolean = rows.heightPx(week, scale) > DrawnWidget.listPx(scale, heightDp, headerPx)
}
