package dev.cupr.calendar.render

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Converts dp to the bitmap's pixels at a display density. */
class Scale(
    val density: Float,
) {
    fun px(dp: Float): Float = dp * density

    /** A 1 dp line in whole pixels, so it stays sharp. */
    val hairline: Float = max(1f, floor(density))

    /** The bitmap density (dpi) to tag a bitmap drawn at this scale with. */
    val dpi: Int = (density * DPI_PER_DENSITY).roundToInt()

    private companion object {
        const val DPI_PER_DENSITY = 160f
    }
}
