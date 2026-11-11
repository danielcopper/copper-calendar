package dev.cupr.calendar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import dev.cupr.calendar.days.DAYS_IN_WEEK
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.render.PhoneMonth.CHEVRON
import dev.cupr.calendar.render.PhoneMonth.FAB_ICON
import dev.cupr.calendar.render.PhoneMonth.FAB_MARGIN
import dev.cupr.calendar.render.PhoneMonth.FAB_SHADOW_BLUR
import dev.cupr.calendar.render.PhoneMonth.FAB_SHADOW_Y
import dev.cupr.calendar.render.PhoneMonth.FAB_SIZE
import dev.cupr.calendar.render.PhoneMonth.HEADS_BOTTOM
import dev.cupr.calendar.render.PhoneMonth.HEAD_FONT
import dev.cupr.calendar.render.PhoneMonth.LETTER_SPACING
import dev.cupr.calendar.render.PhoneMonth.TITLE_BOTTOM
import dev.cupr.calendar.render.PhoneMonth.TITLE_FONT
import dev.cupr.calendar.render.PhoneMonth.TITLE_GAP
import dev.cupr.calendar.render.PhoneMonth.TITLE_ROW
import dev.cupr.calendar.render.PhoneMonth.TITLE_TOP
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Draws the widgets' fixed parts: the header (title, a week's range, chevron,
 * weekday heads) and the round "+". It is given the weekday heads, Monday to
 * Sunday.
 */
class ChromeRenderer(
    private val fonts: Fonts,
    private val icons: Icons,
    private val weekdays: List<String>,
) {
    init {
        require(weekdays.size == DAYS_IN_WEEK) { "The header needs $DAYS_IN_WEEK weekday heads, not ${weekdays.size}" }
    }

    /** "NOVEMBER 2026" and the chevron at the right, then the weekday heads, one per column. */
    fun header(
        month: YearMonth,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
    ): Bitmap = draw(title(month), null, weekdays, widthPx, scale, palette)

    /**
     * A week widget's header: its title ("DIESE WOCHE") and at the right its
     * range ("9.–15. Nov.") and the chevron; with from, below them the weekday
     * heads one per column, from that weekday on.
     */
    fun weekHeader(
        title: String,
        range: String,
        from: DayOfWeek?,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val heads = from?.let { first -> List(DAYS_IN_WEEK) { weekdays[(first.value - 1 + it) % DAYS_IN_WEEK] } }
        return draw(title, range, heads, widthPx, scale, palette)
    }

    private fun draw(
        text: String,
        range: String?,
        heads: List<String>?,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val headPaint = fonts.paint(scale, HEAD_FONT, FontWeight.BOLD, palette.tertiaryText)
        headPaint.letterSpacing = LETTER_SPACING
        headPaint.textAlign = Paint.Align.CENTER
        val headsLine = headPaint.fontMetrics.let { it.descent - it.ascent }
        val titleRow = scale.px(TITLE_TOP + TITLE_ROW + TITLE_BOTTOM)
        val heightPx = ceil(if (heads == null) titleRow else titleRow + headsLine + scale.px(HEADS_BOTTOM)).toInt()
        val bitmap = createBitmap(widthPx, heightPx)
        bitmap.density = scale.dpi
        val canvas = Canvas(bitmap)

        val rowTop = scale.px(TITLE_TOP)
        val rowHeight = scale.px(TITLE_ROW)
        val chevron = scale.px(CHEVRON)
        var end = widthPx - chevron
        if (range != null) {
            val rangePaint = rangePaint(scale, palette.tertiaryText)
            val (shown, x) = placeRange(range, rangePaint, end - scale.px(RANGE_GAP))
            canvas.drawText(shown, x, rangePaint.baselineIn(rowTop, rowHeight), rangePaint)
            end = x
        }
        val title = fonts.paint(scale, TITLE_FONT, FontWeight.SEMI_BOLD, palette.secondaryText)
        title.letterSpacing = LETTER_SPACING
        canvas.drawCut(
            text.uppercase(Locale.GERMAN),
            0f,
            title.baselineIn(rowTop, rowHeight),
            end - scale.px(TITLE_GAP),
            title,
        )
        icons.path(CHEVRON_ICON)?.let {
            canvas.drawIcon(it, widthPx - chevron, rowTop + (rowHeight - chevron) / 2, chevron, palette.tertiaryText)
        }

        if (heads != null) {
            val column = widthPx / DAYS_IN_WEEK.toFloat()
            val baseline = headPaint.baselineIn(titleRow, headsLine)
            heads.forEachIndexed { index, name ->
                canvas.drawText(name.uppercase(Locale.GERMAN), (index + HALF) * column, baseline, headPaint)
            }
        }
        return bitmap
    }

    /**
     * A week's range as the header draws it in a header widthPx wide: the
     * text, cut with "…" where it is wider than the room before the chevron,
     * and the x it starts at, never left of the header.
     */
    internal fun range(
        range: String,
        widthPx: Int,
        scale: Scale,
    ): Pair<String, Float> = placeRange(range, rangePaint(scale, 0), widthPx - scale.px(CHEVRON) - scale.px(RANGE_GAP))

    private fun rangePaint(
        scale: Scale,
        color: Int,
    ): TextPaint = fonts.paint(scale, RANGE_FONT, FontWeight.REGULAR, color).apply { fontFeatureSettings = "tnum" }

    /** The range cut to fit before end, and the x it starts at. */
    private fun placeRange(
        range: String,
        paint: TextPaint,
        end: Float,
    ): Pair<String, Float> {
        val shown = TextUtils.ellipsize(range, paint, max(end, 0f), TextUtils.TruncateAt.END).toString()
        return shown to end - paint.measureText(shown)
    }

    /**
     * The round "+": the accent's circle with its shadow and the plus in the
     * on-accent colour, inside a square that leaves room for the shadow up to
     * the widget's edge ([FAB_MARGIN] on each side).
     */
    fun fab(
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val sidePx = ceil(scale.px(FAB_SIZE + 2 * FAB_MARGIN)).toInt()
        val bitmap = createBitmap(sidePx, sidePx)
        bitmap.density = scale.dpi
        val canvas = Canvas(bitmap)
        val centre = scale.px(FAB_MARGIN + FAB_SIZE / 2)
        val circle =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                setShadowLayer(scale.px(SHADOW_RADIUS), 0f, scale.px(FAB_SHADOW_Y), palette.shadow)
            }
        canvas.drawCircle(centre, centre, scale.px(FAB_SIZE / 2), circle)
        icons.path(PLUS_ICON)?.let {
            val icon = scale.px(FAB_ICON)
            canvas.drawIcon(it, centre - icon / 2, centre - icon / 2, icon, palette.onAccent)
        }
        return bitmap
    }

    companion object {
        // A blur of b is a Gaussian sigma of b / 2, which Android's shadow radius
        // r gives as 0.57735 r + 0.5.
        private const val SHADOW_RADIUS = (FAB_SHADOW_BLUR / 2 - 0.5f) / 0.57735f

        private const val HALF = 0.5f

        /** A week's range beside the chevron: its text size and its gap to the chevron (the tile's). */
        private const val RANGE_FONT = 12f
        private const val RANGE_GAP = 2f
        private const val CHEVRON_ICON = "chevron-right"
        private const val PLUS_ICON = "plus"

        /** "November 2026" */
        fun title(month: YearMonth): String =
            "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)} ${month.year}"
    }
}
