package dev.cupr.calendar.render

import androidx.annotation.ColorInt
import kotlin.math.roundToInt

/** A colour with its alpha multiplied by an opacity (0 to 1). */
@ColorInt
internal fun @receiver:ColorInt Int.withOpacity(opacity: Float): Int {
    requireOpacity(opacity)
    val alpha = ((this ushr ALPHA_SHIFT) * opacity).roundToInt()
    return (alpha shl ALPHA_SHIFT) or (this and RGB_MASK)
}

/** An opacity (0 to 1) as a layer alpha (0 to 255). */
internal fun opacityAlpha(opacity: Float): Int {
    requireOpacity(opacity)
    return (opacity * FULL_ALPHA).roundToInt()
}

private fun requireOpacity(opacity: Float) = require(opacity in 0f..1f) { "An opacity runs from 0 to 1, not $opacity" }

private const val ALPHA_SHIFT = 24
private const val RGB_MASK = 0x00FFFFFF
private const val FULL_ALPHA = 255
