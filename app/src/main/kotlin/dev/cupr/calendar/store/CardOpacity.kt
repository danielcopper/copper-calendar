package dev.cupr.calendar.store

import kotlin.math.roundToInt

/**
 * A widget's card's opacity in percent (D64): from 0, no card, to 100, the
 * whole card, the default, in steps of 10. Made outside this class only by
 * [of], [ofStored] and [nearest], so every opacity the app carries is one
 * of [steps].
 */
@JvmInline
value class CardOpacity private constructor(
    val percent: Int,
) {
    /** Whether the card is drawn at all: not at 0 %. */
    val shown: Boolean get() = percent > 0

    /** The opacity as a share of the whole card, 0 to 1. */
    val fraction: Float get() = percent / MAX.toFloat()

    /** The name it is kept under in a widget's settings: its percent, "0" to "100" ([ofStored]). */
    val stored: String get() = percent.toString()

    companion object {
        private const val MAX = 100
        private const val STEP = 10

        /** The whole card, the default. */
        val FULL = CardOpacity(MAX)

        /** Every opacity the setup offers, from none to full. */
        val steps: List<CardOpacity> = (0..MAX step STEP).map(::CardOpacity)

        /** The opacity of percent; none when it is no step of 10 from 0 to 100. */
        fun of(percent: Int): CardOpacity? = CardOpacity(percent).takeIf { percent in 0..MAX && percent % STEP == 0 }

        /** The opacity kept under name ([stored]); none when name is no number or no step. */
        fun ofStored(name: String): CardOpacity? = name.toIntOrNull()?.let(::of)

        /** The step nearest to percent, within 0 and 100, as a slider gives it. */
        fun nearest(percent: Float): CardOpacity = CardOpacity(((percent / STEP).roundToInt() * STEP).coerceIn(0, MAX))
    }
}
