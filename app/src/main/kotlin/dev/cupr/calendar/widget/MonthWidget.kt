package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import dev.cupr.calendar.R
import dev.cupr.calendar.month.MonthLayout
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.render.WeekRowRenderer
import dev.cupr.calendar.sample.SampleData
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * The month widget's views (D8, D23): a fixed header, a list of drawn week
 * rows with a tap area per day, and a fixed "+" above the list. Each drawn
 * part comes twice, light and dark; the layouts show one of the two by
 * night-mode resources (`values-night/integers.xml`), so the launcher
 * switches the mode without the app's process.
 */
object MonthWidget {
    internal const val TAG = "MonthWidget"

    /** The plain text it shows when the system refuses its views (D40). */
    internal val TOO_FULL = R.string.widget_month_too_full

    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        val width = DrawnWidget.widthDp(manager.getAppWidgetOptions(id), TAG, id)
        val drawn = draw(context, width, id, BitmapLimit.limitBytes(context, TAG, id))
        DrawnWidget.push(context, id, drawn, TOO_FULL, TAG) { manager.updateAppWidget(id, it) }
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
    ): RemoteViews = draw(context, widthDp, id, BitmapLimit.limitBytes(context, TAG, id)).views

    /**
     * The widget at widthDp and the bytes of its bitmaps. The month's cells
     * show at most two entries each, so it leaves nothing out for the limit;
     * the log warns when its weeks pass it, and [DrawnWidget.push] falls back.
     */
    internal fun draw(
        context: Context,
        widthDp: Int,
        id: Int,
        limit: Long,
    ): Drawn {
        val today = LocalDate.now(SampleData.clock)
        val view = MonthLayout.build(YearMonth.from(today), SampleData.entries, today)
        val scale = Scale(context.resources.displayMetrics.density)
        // The drawn rows and header span the widget less the card's inset at both sides.
        val widthPx = scale.px(widthDp - 2 * PhoneMonth.CARD_INSET).roundToInt()
        val assets = RenderAssets.of(context)
        val rows = WeekRowRenderer(assets.fonts, assets.icons, assets.drawings)
        val kept = Kept()
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        val title = ChromeRenderer.title(view.month)
        DrawnWidget.fixedParts(context, views, kept, title, today, scale) { chrome, palette ->
            chrome.header(view.month, widthPx, scale, palette)
        }
        val needed = kept.bytes + view.weeks.sumOf { BitmapLimit.bytes(widthPx, rows.heightPx(it, scale)) }
        BitmapLimit.warnOver(TAG, id, needed = needed, limit = limit, left = 0, after = needed)
        DrawnWidget.warnMarks(
            TAG,
            id,
            view.weeks.flatMap { rows.marksWithoutRoom(it, widthPx, scale).toList() }.toMap(),
        )
        val items =
            view.weeks.map { week ->
                val light = kept.keep(rows.render(week, widthPx, scale, SampleData.light))
                val dark = kept.keep(rows.render(week, widthPx, scale, SampleData.dark))
                week.days
                    .first()
                    .date
                    .toEpochDay() to DrawnWidget.weekRow(context, week, light, dark)
            }
        DrawnWidget.list(context, views, items)
        views.setScrollPosition(R.id.list, view.todayWeek)
        DrawnWidget.logDrawn(TAG, id, kept, widthPx, limit)
        return kept.drawn(views, limit)
    }
}
