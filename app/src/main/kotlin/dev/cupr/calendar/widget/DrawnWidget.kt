package dev.cupr.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import dev.cupr.calendar.R
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.days.longName
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.Week
import dev.cupr.calendar.render.ChromeRenderer
import dev.cupr.calendar.render.HeaderNote
import dev.cupr.calendar.render.PhoneMonth
import dev.cupr.calendar.render.RenderAssets
import dev.cupr.calendar.render.Scale
import dev.cupr.calendar.tap.Tap
import dev.cupr.calendar.tap.Taps
import java.time.LocalDate

/** The bitmaps a widget's views hold, kept as they are drawn, to count their bytes. */
internal class Kept {
    private val bitmaps = mutableListOf<Bitmap>()

    val count: Int get() = bitmaps.size
    val bytes: Long get() = bitmaps.sumOf { it.allocationByteCount.toLong() }

    fun keep(bitmap: Bitmap): Bitmap = bitmap.also { bitmaps += it }

    /** The views holding these bitmaps, drawn for limit. */
    fun drawn(
        views: RemoteViews,
        limit: Long,
    ) = Drawn(views, bytes, limit)
}

/**
 * The month's or "Woche"'s height as the launcher reports it: its greatest
 * height in dp, its height in portrait (see [DrawnWidget.widthDp]), and its
 * least height in dp when the launcher reports one, which the log names.
 */
internal class WidgetHeight(
    val dp: Int,
    val leastDp: Int?,
) {
    init {
        require(dp > 0) { "A widget's height is above 0 dp, not $dp" }
        require(leastDp == null || leastDp > 0) { "A widget's least height is above 0 dp, not $leastDp" }
    }

    /** The height, the option it came from, and the least height beside it, for the log. */
    val forLog: String
        get() {
            val least = leastDp?.let { "$it dp least" } ?: "no least height"
            return "$dp dp (${AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT}, $least)"
        }

    companion object {
        /**
         * The height the launcher reports in options; null when it reports no
         * greatest height, which the log says: the list then scrolls today
         * only into view (D27).
         */
        fun of(
            options: Bundle,
            tag: String,
            id: Int,
        ): WidgetHeight? {
            val key = AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            val dp = options.getInt(key).takeIf { it > 0 }
            if (dp == null) {
                Log.w(tag, "Widget $id: the launcher reports no $key, so its list scrolls today only into view")
                return null
            }
            val least = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
            return WidgetHeight(dp, least)
        }
    }
}

/** A widget's views, the bytes of the bitmaps they hold as the app counts them, and the limit it drew them for. */
internal class Drawn(
    val views: RemoteViews,
    val bytes: Long,
    val limit: Long,
)

/**
 * What the drawn widgets share: their size as the launcher reports it, their
 * header and "+", their list, and their update with its fallback when the
 * system refuses their views; the limit on their bitmaps is [BitmapLimit].
 */
internal object DrawnWidget {
    /** A widget's width before the launcher reports one: 4 cells (design.md). */
    const val DEFAULT_WIDTH_DP = 276

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

    /**
     * The height in px of a widget's list below a header headerPx high, in a
     * widget heightDp high: the widget less the card's border and padding at
     * the top and bottom, and less the header. "+" stands over the list and
     * takes none of it. "Woche quer" passes its least height
     * ([WeekColumnsWidget.update]), the month and "Woche" their greatest (D27).
     */
    fun listPx(
        scale: Scale,
        heightDp: Int,
        headerPx: Int,
    ): Float = scale.px(heightDp - 2 * (PhoneMonth.CARD_BORDER + PhoneMonth.CARD_END)) - headerPx

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

    /** The renderer of the fixed parts, with the weekday heads. */
    private fun chrome(context: Context): ChromeRenderer {
        val assets = RenderAssets.of(context)
        return ChromeRenderer(
            assets.fonts,
            assets.icons,
            context.resources.getStringArray(R.array.weekday_heads).toList(),
        )
    }

    /**
     * The header and "+" of calendar: the header drawn light and dark by
     * header with the renderer of the fixed parts and the calendar's notice
     * at its right, named by description and the notice, and opening the
     * calendar page, or the sign-in screen while the notice asks to sign in
     * again (D52); "+" opening a new entry on today. Returns the drawn
     * header's height in px, which the list's height leaves out.
     */
    fun fixedParts(
        context: Context,
        views: RemoteViews,
        kept: Kept,
        description: String,
        calendar: Content.Calendar,
        scale: Scale,
        header: (ChromeRenderer, Palette, HeaderNote?) -> Bitmap,
    ): Int {
        val chrome = chrome(context)
        val notice = calendar.notice
        val (note, headerTap) =
            when (notice) {
                null -> null to Taps.pendingIntent(context, Tap.Header)
                is Notice.Stale -> HeaderNote(notice.text, accent = false) to Taps.pendingIntent(context, Tap.Header)
                is Notice.SignInAgain -> HeaderNote(notice.text, accent = true) to Taps.signIn(context)
            }
        val palettes = calendar.palettes
        val light = kept.keep(header(chrome, palettes.light, note))
        views.setImageViewBitmap(R.id.header_light, light)
        views.setImageViewBitmap(R.id.header_dark, kept.keep(header(chrome, palettes.dark, note)))
        views.setContentDescription(R.id.header, notice?.let { "$description, ${it.text}" } ?: description)
        views.setOnClickPendingIntent(R.id.header, headerTap)
        views.setImageViewBitmap(R.id.fab_light, kept.keep(chrome.fab(scale, palettes.light)))
        views.setImageViewBitmap(R.id.fab_dark, kept.keep(chrome.fab(scale, palettes.dark)))
        views.setOnClickPendingIntent(R.id.fab, Taps.pendingIntent(context, Tap.New(calendar.today)))
        return light.height
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
        val of = BitmapLimit.describe(limit)
        Log.i(tag, "Widget $id: drew ${kept.count} bitmaps at ${widthPx}px wide, ${kept.bytes} bytes of $of$extra")
    }

    /** Warns once per drawing for each day whose marks beside its number have no room, and how many. */
    fun warnMarks(
        tag: String,
        id: Int,
        withoutRoom: Map<LocalDate, Int>,
    ) {
        for ((date, count) in withoutRoom) {
            Log.w(tag, "Widget $id: $date has no room beside its number for $count of its marks")
        }
    }

    /**
     * Hands drawn to the launcher through update. When the system refuses it
     * for its bitmaps, which it does by throwing an IllegalArgumentException
     * (see [BitmapLimit.limitBytes]), the log says so and the widget shows
     * the plain tooFull text instead, which opens the calendar page. Only
     * that exception, and only from update, is caught here; when the system
     * refuses the plain text too, that exception propagates.
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
            Log.e(
                tag,
                "Widget $id: the system refused its views; the app counted ${drawn.bytes} bytes of bitmaps " +
                    "against ${BitmapLimit.describe(drawn.limit)}, the limit it read from the display",
                e,
            )
            update(plainViews(context, tooFull))
        }
    }

    /**
     * The plain views of a widget that shows a text instead of its drawing:
     * text on its card, opening tap, by default the calendar page.
     */
    fun plainViews(
        context: Context,
        @StringRes text: Int,
        tap: PendingIntent = Taps.pendingIntent(context, Tap.Header),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_plain)
        views.setTextViewText(R.id.plain_text, context.getString(text))
        views.setOnClickPendingIntent(R.id.plain_text, tap)
        return views
    }
}

/**
 * A provider of a drawn widget: drawn anew on each update and whenever its
 * size changes, from what the app has to show ([Content]); without a
 * calendar to draw, it shows [Content.NoData]'s plain text, which opens the
 * sign-in screen (D50). Each widget is updated on its own: when its update throws an
 * IllegalArgumentException or an IllegalStateException, which a check in
 * the drawing throws, the log says so and the widget shows the plain text
 * [R.string.widget_failed], which opens the calendar page; the others are
 * updated all the same. No other exception is caught, and a plain text the
 * system refuses here is not caught again: its exception propagates, and the
 * widgets after it in that update are not updated (D40).
 */
abstract class DrawnWidgetProvider : AppWidgetProvider() {
    /** The log's tag. */
    protected abstract val tag: String

    /** Draws calendar on the widget id and hands it to the launcher. */
    protected abstract fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        calendar: Content.Calendar,
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        synchronized(Widgets.lock) { updateAll(context, appWidgetManager, appWidgetIds, content(context)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        synchronized(Widgets.lock) { updateOne(context, appWidgetManager, appWidgetId, content(context)) }
    }

    /** Updates this provider's widgets with content, which the app's own updates read once for all ([Widgets]). */
    internal fun updateAll(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        content: Content,
    ) {
        ids.forEach { updateOne(context, manager, it, content) }
    }

    /** This provider's widgets on the home screen. */
    internal fun ids(
        context: Context,
        manager: AppWidgetManager,
    ): IntArray = manager.getAppWidgetIds(ComponentName(context, javaClass))

    private fun updateOne(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        content: Content,
    ) {
        when (content) {
            is Content.NoData -> {
                manager.updateAppWidget(id, DrawnWidget.plainViews(context, content.text, Taps.signIn(context)))
            }

            is Content.Calendar -> {
                try {
                    update(context, manager, id, content)
                } catch (e: IllegalArgumentException) {
                    failed(context, manager, id, e)
                } catch (e: IllegalStateException) {
                    failed(context, manager, id, e)
                }
            }
        }
    }

    private fun failed(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        error: RuntimeException,
    ) {
        Log.e(tag, "Widget $id: could not be drawn", error)
        manager.updateAppWidget(id, DrawnWidget.plainViews(context, R.string.widget_failed))
    }

    companion object {
        /**
         * What the widgets show now ([Content.read]), read once per update of
         * a provider's widgets and once for all in the app's own updates
         * ([Widgets]); tests replace it to draw the sample month.
         */
        @VisibleForTesting
        internal var content: (Context) -> Content = { Content.read(it) }
    }
}
