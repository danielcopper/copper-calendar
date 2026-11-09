package dev.cupr.calendar.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import dev.cupr.calendar.model.Palette
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

/**
 * Draws the month widget's fixed parts as the mockup Month.dc.html does:
 * the header (month title, chevron, weekday heads) and the round "+".
 */
class ChromeRenderer(
    private val fonts: Fonts,
    private val icons: Icons,
) {
    /** "NOVEMBER 2026" and the chevron (`.ch`, `.ct`, `.trng`), then the weekday heads (`.wdh`). */
    fun header(
        month: YearMonth,
        widthPx: Int,
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val heads = fonts.paint(scale, HEAD_FONT, PhoneMonth.BOLD, palette.tertiaryText)
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
        val title = fonts.paint(scale, TITLE_FONT, PhoneMonth.SEMI_BOLD, palette.secondaryText)
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

        val column = widthPx / WEEKDAYS.size.toFloat()
        val baseline = heads.baselineIn(titleRow, headsLine)
        WEEKDAYS.forEachIndexed { index, name ->
            canvas.drawText(name.uppercase(Locale.GERMAN), (index + HALF) * column, baseline, heads)
        }
        return bitmap
    }

    /**
     * The round "+" (`.fab`): the accent's circle with its shadow and the
     * plus in the on-accent colour, inside a square that leaves room for the
     * shadow up to the widget's edge ([FAB_MARGIN] on each side).
     */
    fun fab(
        scale: Scale,
        palette: Palette,
    ): Bitmap {
        val side = scale.px(FAB_SIZE + 2 * FAB_MARGIN)
        val sidePx = ceil(side).toInt()
        val bitmap = createBitmap(sidePx, sidePx)
        bitmap.density = scale.dpi
        val canvas = Canvas(bitmap)
        val centre = scale.px(FAB_MARGIN + FAB_SIZE / 2)
        val circle =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent
                setShadowLayer(scale.px(SHADOW_RADIUS), 0f, scale.px(SHADOW_Y), palette.shadow)
            }
        canvas.drawCircle(centre, centre, scale.px(FAB_SIZE / 2), circle)
        icons.path(PLUS_ICON)?.let {
            val icon = scale.px(FAB_ICON)
            canvas.drawIcon(it, centre - icon / 2, centre - icon / 2, icon, palette.onAccent)
        }
        return bitmap
    }

    companion object {
        /** "+" is 40 dp across, 12 dp inside the card's 1 dp border. */
        const val FAB_SIZE = 40f
        const val FAB_MARGIN = 13f

        private const val FAB_ICON = 24f

        // box-shadow: 0 4px 14px; a CSS blur of 14 px is a Gaussian sigma of 7,
        // which Android's shadow radius r gives as 0.57735 r + 0.5.
        private const val SHADOW_Y = 4f
        private const val SHADOW_RADIUS = 11.26f

        private const val TITLE_TOP = 2f
        private const val TITLE_ROW = 32f
        private const val TITLE_BOTTOM = 4f
        private const val TITLE_FONT = 13f
        private const val TITLE_GAP = 8f
        private const val CHEVRON = 16f
        private const val HEAD_FONT = 10.5f
        private const val HEADS_BOTTOM = 6f
        private const val LETTER_SPACING = 0.06f
        private const val HALF = 0.5f
        private const val CHEVRON_ICON = "chevron-right"
        private const val PLUS_ICON = "plus"
        private val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

        /** "November 2026" */
        fun title(month: YearMonth): String =
            "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)} ${month.year}"
    }
}
