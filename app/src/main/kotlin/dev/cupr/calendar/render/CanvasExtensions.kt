package dev.cupr.calendar.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.caverock.androidsvg.RenderOptions
import dev.cupr.calendar.model.Entry

/** Text in Roboto at a size in dp and a font weight. */
internal fun Fonts.paint(
    scale: Scale,
    sizeDp: Float,
    weight: FontWeight,
    @ColorInt color: Int,
): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = weight(weight)
        textSize = scale.px(sizeDp)
        this.color = color
    }

/** The baseline that centres a line of this paint's text, from its ascent to its descent, in a box. */
internal fun TextPaint.baselineIn(
    top: Float,
    height: Float,
): Float {
    val metrics = fontMetrics
    return top + (height - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
}

/** The text cut with "…" where this paint would draw it wider than maxWidth; empty without room. */
internal fun TextPaint.cut(
    text: String,
    maxWidth: Float,
): String = if (maxWidth <= 0f) "" else TextUtils.ellipsize(text, this, maxWidth, TextUtils.TruncateAt.END).toString()

/** Draws text from x, cut with "…" where it would pass maxWidth. */
internal fun Canvas.drawCut(
    text: String,
    x: Float,
    baseline: Float,
    maxWidth: Float,
    paint: TextPaint,
) {
    val cut = paint.cut(text, maxWidth)
    if (cut.isNotEmpty()) drawText(cut, x, baseline, paint)
}

internal fun Canvas.fill(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    @ColorInt color: Int,
) {
    drawRect(left, top, right, bottom, Paint().apply { this.color = color })
}

internal fun Canvas.fill(
    path: Path,
    @ColorInt color: Int,
) {
    drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

/** Draws an MDI outline as a size × size icon at (x, y). */
internal fun Canvas.drawIcon(
    path: Path,
    x: Float,
    y: Float,
    size: Float,
    @ColorInt color: Int,
) {
    withTranslation(x, y) {
        withScale(size / Icons.BOX, size / Icons.BOX) { fill(path, color) }
    }
}

/** Draws a drawing into a size × size box at (x, y). */
internal fun Canvas.drawDrawing(
    drawing: com.caverock.androidsvg.SVG,
    x: Float,
    y: Float,
    size: Float,
) {
    drawing.renderToCanvas(this, RenderOptions().viewPort(x, y, size, size))
}

/**
 * An entry's mark in a size × size box at (x, y), wherever a mark stands (a
 * month cell's line, chip or number, a week row's line or chip): its drawing,
 * else its icon in color, else a dot dot wide in color, also for a drawing or
 * icon the app does not ship (D22, D24). The icon is looked up only when no
 * drawing is drawn.
 */
internal fun Canvas.drawMark(
    entry: Entry,
    @ColorInt color: Int,
    x: Float,
    y: Float,
    size: Float,
    dot: Float,
    icons: Icons,
    drawings: Drawings,
) {
    val drawing = entry.drawing?.let(drawings::get)
    if (drawing != null) {
        drawDrawing(drawing, x, y, size)
        return
    }
    val icon = entry.icon?.let(icons::path)
    if (icon != null) {
        drawIcon(icon, x, y, size, color)
    } else {
        drawCircle(x + size / 2, y + size / 2, dot / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
}

/** A rectangle with separate radii at its left and right ends. */
internal fun roundedPath(
    rect: RectF,
    left: Float,
    right: Float,
): Path =
    Path().apply {
        addRoundRect(rect, floatArrayOf(left, left, right, right, right, right, left, left), Path.Direction.CW)
    }

/**
 * The coloured band along a shape's left edge, following its rounded
 * corners: the shape less itself moved right by width.
 */
internal fun insetBand(
    shape: Path,
    width: Float,
): Path {
    val moved = Path(shape).apply { offset(width, 0f) }
    return Path(shape).apply { op(moved, Path.Op.DIFFERENCE) }
}
