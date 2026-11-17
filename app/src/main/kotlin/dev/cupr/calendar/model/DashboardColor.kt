package dev.cupr.calendar.model

import androidx.annotation.ColorInt

/**
 * The colour the dashboard shows an entry in, as Home Assistant resolves it
 * (the sensor's `resolved_color`, D49): a fixed colour, a theme variable of
 * a calendar, or the secondary text colour. The last two differ by mode, so
 * a [Palette] resolves them.
 */
sealed interface DashboardColor {
    /** A colour that is the same in light and dark (ARGB). */
    data class Fixed(
        @param:ColorInt val argb: Int,
    ) : DashboardColor

    /** A calendar's theme variable, one of [CalendarVariables.all]. */
    data class ThemeVariable(
        val name: String,
    ) : DashboardColor {
        init {
            require(name in CalendarVariables.all) { "\"$name\" is not one of ${CalendarVariables.all}" }
        }
    }

    /** `secondary-text-color`, the grey of a calendar without a colour. */
    data object SecondaryText : DashboardColor
}
