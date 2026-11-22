package dev.cupr.calendar.store

import kotlin.math.roundToInt

/**
 * A widget's card's opacity in percent (D64): from 0, no card, to 100, the
 * whole card, the default, in steps of 10. Made only by [of] and
 * [nearest], so every opacity the app carries is one of [steps].
 */
@JvmInline
value class CardOpacity private constructor(
    val percent: Int,
) {
    /** Whether the card is drawn at all: not at 0 %. */
    val shown: Boolean get() = percent > 0

    /**
     * The card's alpha as a widget's views set it, 0 to 255: its share of
     * 255, rounded half up.
     */
    val alpha: Int get() = (percent * OPAQUE + MAX / 2) / MAX

    companion object {
        private const val MAX = 100
        private const val STEP = 10
        private const val OPAQUE = 255

        /** The whole card, the default. */
        val FULL = CardOpacity(MAX)

        /** Every opacity the setup offers, from none to full. */
        val steps: List<CardOpacity> = (0..MAX step STEP).map(::CardOpacity)

        /** The opacity of percent; none when it is no step of 10 from 0 to 100. */
        fun of(percent: Int): CardOpacity? = CardOpacity(percent).takeIf { percent in 0..MAX && percent % STEP == 0 }

        /** The step nearest to percent, within 0 and 100, as a slider gives it. */
        fun nearest(percent: Float): CardOpacity = CardOpacity(((percent / STEP).roundToInt() * STEP).coerceIn(0, MAX))
    }
}
