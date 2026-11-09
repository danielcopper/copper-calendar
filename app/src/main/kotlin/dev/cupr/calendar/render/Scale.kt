package dev.cupr.calendar.render

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Converts the mockup's CSS pixels (dp on the phone) to bitmap pixels. */
class Scale(
    val density: Float,
) {
    fun px(dp: Float): Float = dp * density

    /** A CSS 1 px border, as the browser snaps it to whole device pixels. */
    val hairline: Float = max(1f, floor(density))

    /** The bitmap density (dpi) to tag a bitmap drawn at this scale with. */
    val dpi: Int = (density * DPI_PER_DENSITY).roundToInt()

    private companion object {
        const val DPI_PER_DENSITY = 160f
    }
}
