package dev.cupr.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.annotation.StringRes
import dev.cupr.calendar.R
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.days.longName
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.sample.SampleData
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import java.time.LocalDate

/** The bitmaps a widget's views hold, kept as they are drawn, to count their bytes. */
internal class Kept {
    private val bitmaps = mutableListOf<Bitmap>()

    val count: Int get() = bitmaps.size
    val bytes: Long get() = bitmaps.sumOf { it.allocationByteCount.toLong() }

    fun keep(bitmap: Bitmap): Bitmap = bitmap.also { bitmaps += it }
}

/** A widget's views, the bytes of the bitmaps they hold, and the limit the system holds them to. */
internal class Drawn(
    val views: RemoteViews,
    val bytes: Long,
    val limit: Long,
)

/**
 * What the drawn widgets share: their size as the launcher reports it, the
 * limit on their bitmaps, their header and "+", their list, and their update
 * with its fallback when the system refuses their views.
 */
internal object DrawnWidget {
    /** A widget's width before the launcher reports one: 4 cells (design.md). */
    const val DEFAULT_WIDTH_DP = 276

    /** The bytes of an ARGB_8888 bitmap, as [Bitmap.getAllocationByteCount] counts them. */
    private const val BYTES_PER_PIXEL = 4

    /** The system's limit is this many times the display's pixels in bytes. */
    private const val LIMIT_PER_PIXEL = 6

    /** Each drawn part comes twice, light and dark. */
    const val MODES = 2

    private val dayIds =
        intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)

    init {
        check(dayIds.size == DAYS_IN_WEEK)
    }

    /**
     * The widget's width in dp. In portrait, the orientation the phones use,
     * a widget is as wide as its least width and as tall as its greatest
     * height; in landscape the other way round. Without a reported width,
     * 4 cells, and the log says so.
     */
    fun widthDp(
        options: Bundle,
        tag: String,
        id: Int,
    ): Int = option(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_WIDTH_DP, tag, id)

    /** An option the launcher reports in dp, or default when it reports none, which the log says. */
    fun option(
        options: Bundle,
        key: String,
        default: Int,
        tag: String,
        id: Int,
    ): Int =
        options.getInt(key).takeIf { it > 0 } ?: default.also {
            Log.w(tag, "Widget $id: the launcher reports no $key, so it is drawn for $default dp")
        }

    /**
     * The limit on the bytes of a widget's bitmaps: 6 × the display's width ×
     * its height in pixels, as AOSP's `AppWidgetServiceImpl` computes it
     * (`computeMaximumWidgetBitmapMemory`, from the display's real size) and
     * enforces it (`updateAppWidgetInstanceLocked` throws an
     * IllegalArgumentException above it). The display's size is read from the
     * window manager's maximum window metrics, its full size.
     */
    fun limitBytes(context: Context): Long {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        return LIMIT_PER_PIXEL.toLong() * bounds.width() * bounds.height()
    }

    /** The bytes of a bitmap widthPx × heightPx, light and dark. */
    fun bytes(
        widthPx: Int,
        heightPx: Int,
    ): Long = MODES.toLong() * BYTES_PER_PIXEL * widthPx * heightPx

    /** The renderer of the fixed parts, with the weekday heads. */
    fun chrome(context: Context): ChromeRenderer {
        val assets = RenderAssets.of(context)
        return ChromeRenderer(
            assets.fonts,
            assets.icons,
            context.resources.getStringArray(R.array.weekday_heads).toList(),
        )
    }

    /**
     * The header and "+": the header drawn light and dark by header, named by
     * description and opening the calendar page; "+" opening a new entry on
     * today.
     */
    fun fixedParts(
        context: Context,
        views: RemoteViews,
        kept: Kept,
        chrome: ChromeRenderer,
        description: String,
        today: LocalDate,
        scale: Scale,
        header: (Palette) -> Bitmap,
    ) {
        views.setImageViewBitmap(R.id.header_light, kept.keep(header(SampleData.light)))
        views.setImageViewBitmap(R.id.header_dark, kept.keep(header(SampleData.dark)))
        views.setContentDescription(R.id.header, description)
        views.setOnClickPendingIntent(R.id.header, Taps.pendingIntent(context, Tap.Header))
        views.setImageViewBitmap(R.id.fab_light, kept.keep(chrome.fab(scale, SampleData.light)))
        views.setImageViewBitmap(R.id.fab_dark, kept.keep(chrome.fab(scale, SampleData.dark)))
        views.setOnClickPendingIntent(R.id.fab, Taps.pendingIntent(context, Tap.New(today)))
    }

    /** The list's rows, each by its stable id, and the template their taps fill in. */
    fun list(
        context: Context,
        views: RemoteViews,
        rows: List<Pair<Long, RemoteViews>>,
    ) {
        val items =
            RemoteViews.RemoteCollectionItems
                .Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
        rows.forEach { (id, row) -> items.addItem(id, row) }
        views.setRemoteAdapter(R.id.list, items.build())
        views.setPendingIntentTemplate(R.id.list, Taps.template(context))
    }

    /**
     * A drawn week row of seven day columns (widget_month_week.xml), the
     * month's or "Woche quer"'s: the drawing light and dark, and over it a tap
     * area per day, named by the day and opening it.
     */
    fun weekRow(
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
            row.setContentDescription(dayIds[index], day.date.longName)
        }
        return row
    }

    /** The log line of a drawing: the bitmaps, their width and bytes, and the limit. */
    fun logDrawn(
        tag: String,
        id: Int,
        kept: Kept,
        widthPx: Int,
        limit: Long,
        extra: String = "",
    ) {
        Log.i(tag, "Widget $id: drew ${kept.count} bitmaps at ${widthPx}px wide, ${kept.bytes} bytes of $limit$extra")
    }

    /**
     * Hands drawn to the launcher through update. When the system refuses it
     * for its bitmaps, which it does by throwing an IllegalArgumentException
     * (see [limitBytes]), the log says so and the widget shows the plain
     * tooFull text instead, which opens the calendar page.
     */
    fun push(
        context: Context,
        id: Int,
        drawn: Drawn,
        @StringRes tooFull: Int,
        tag: String,
        update: (RemoteViews) -> Unit,
    ) {
        try {
            update(drawn.views)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Widget $id: refused with ${drawn.bytes} bytes of bitmaps, limit ${drawn.limit}", e)
            update(tooFullViews(context, tooFull))
        }
    }

    /** The plain views of a widget too full to draw: its text, opening the calendar page. */
    fun tooFullViews(
        context: Context,
        @StringRes text: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_too_full)
        views.setTextViewText(R.id.too_full, context.getString(text))
        views.setOnClickPendingIntent(R.id.too_full, Taps.pendingIntent(context, Tap.Header))
        return views
    }
}

/**
 * A provider of a drawn widget: drawn anew on each update and whenever its
 * size changes. Each widget is updated on its own, so one the system refuses
 * does not keep the others from theirs.
 */
abstract class DrawnWidgetProvider : AppWidgetProvider() {
    /** The log's tag. */
    protected abstract val tag: String

    /** Draws the widget id and hands it to the launcher. */
    protected abstract fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    private fun updateOne(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
    ) {
        try {
            update(context, manager, id)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Widget $id: not updated", e)
        }
    }
}
