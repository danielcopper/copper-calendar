package dev.cupr.calendar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.month.DAYS_IN_WEEK
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
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Draws the month widget's fixed parts: the header (month title, chevron,
 * weekday heads) and the round "+". The weekday heads, Monday to Sunday,
 * come from the string resource `weekday_heads`.
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
    ): Bitmap {
        val heads = fonts.paint(scale, HEAD_FONT, FontWeight.BOLD, palette.tertiaryText)
        heads.letterSpacing = LETTER_SPACING
        heads.textAlign = Paint.Align.CENTER
        val headsLine = heads.fontMetrics.let { it.descent - it.ascent }
        val titleRow = scale.px(TITLE_TOP + TITLE_ROW + TITLE_BOTTOM)
        val heightPx = ceil(titleRow + headsLine + scale.px(HEADS_BOTTOM)).toInt()
        val bitmap = createBitmap(widthPx, heightPx)
        bitmap.density = scale.dpi
        val canvas = Canvas(bitmap)

        val rowTop = scale.px(TITLE_TOP)
        val rowHeight = scale.px(TITLE_ROW)
        val title = fonts.paint(scale, TITLE_FONT, FontWeight.SEMI_BOLD, palette.secondaryText)
        title.letterSpacing = LETTER_SPACING
        val chevron = scale.px(CHEVRON)
        canvas.drawCut(
            title(month).uppercase(Locale.GERMAN),
            0f,
            title.baselineIn(rowTop, rowHeight),
            widthPx - chevron - scale.px(TITLE_GAP),
            title,
        )
        icons.path(CHEVRON_ICON)?.let {
            canvas.drawIcon(it, widthPx - chevron, rowTop + (rowHeight - chevron) / 2, chevron, palette.tertiaryText)
        }

        val column = widthPx / DAYS_IN_WEEK.toFloat()
        val baseline = heads.baselineIn(titleRow, headsLine)
        weekdays.forEachIndexed { index, name ->
            canvas.drawText(name.uppercase(Locale.GERMAN), (index + HALF) * column, baseline, heads)
        }
        return bitmap
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
        private const val CHEVRON_ICON = "chevron-right"
        private const val PLUS_ICON = "plus"

        /** "November 2026" */
        fun title(month: YearMonth): String =
            "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)} ${month.year}"
    }
}
