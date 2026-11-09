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
import kotlin.math.roundToInt

/** A colour with its alpha multiplied by an opacity (0 to 1). */
@ColorInt
internal fun @receiver:ColorInt Int.withOpacity(opacity: Float): Int {
    require(opacity in 0f..1f) { "An opacity runs from 0 to 1, not $opacity" }
    val alpha = ((this ushr ALPHA_SHIFT) * opacity).roundToInt()
    return (alpha shl ALPHA_SHIFT) or (this and RGB_MASK)
}

/** An opacity (0 to 1) as a layer alpha (0 to 255). */
internal fun opacityAlpha(opacity: Float): Int {
    require(opacity in 0f..1f) { "An opacity runs from 0 to 1, not $opacity" }
    return (opacity * FULL_ALPHA).roundToInt()
}

private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0x00FFFFFF
private const val FULL_ALPHA = 255

/** Text in Roboto at a size in dp and a font weight. */
fun Fonts.paint(
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
fun TextPaint.baselineIn(
    top: Float,
    height: Float,
): Float {
    val metrics = fontMetrics
    return top + (height - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent
}

/** Draws text from x, cut with "…" where it would pass maxWidth. */
fun Canvas.drawCut(
    text: String,
    x: Float,
    baseline: Float,
    maxWidth: Float,
    paint: TextPaint,
) {
    if (maxWidth <= 0f) return
    val cut = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
    drawText(cut, x, baseline, paint)
}

fun Canvas.fill(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Int,
) {
    drawRect(left, top, right, bottom, Paint().apply { this.color = color })
}

fun Canvas.fill(
    path: Path,
    color: Int,
) {
    drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

/** Draws an MDI outline as a size × size icon at (x, y). */
fun Canvas.drawIcon(
    path: Path,
    x: Float,
    y: Float,
    size: Float,
    color: Int,
) {
    withTranslation(x, y) {
        withScale(size / Icons.BOX, size / Icons.BOX) { fill(path, color) }
    }
}

/** Draws a drawing into a size × size box at (x, y). */
fun Canvas.drawDrawing(
    drawing: com.caverock.androidsvg.SVG,
    x: Float,
    y: Float,
    size: Float,
) {
    drawing.renderToCanvas(this, RenderOptions().viewPort(x, y, size, size))
}

/** A rectangle with separate radii at its left and right ends. */
fun roundedPath(
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
fun insetBand(
    shape: Path,
    width: Float,
): Path {
    val moved = Path(shape).apply { offset(width, 0f) }
    return Path(shape).apply { op(moved, Path.Op.DIFFERENCE) }
}
