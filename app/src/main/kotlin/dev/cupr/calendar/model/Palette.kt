package dev.cupr.calendar.model

import androidx.annotation.ColorInt

/**
 * The colours of one theme mode (ARGB), named after the Home Assistant theme
 * variables they come from.
 */
data class Palette(
    /** `card-background-color` */
    @param:ColorInt val card: Int,
    /** `ha-card-border-color` */
    @param:ColorInt val cardBorder: Int,
    /** `primary-text-color` */
    @param:ColorInt val text: Int,
    /** `secondary-text-color` */
    @param:ColorInt val secondaryText: Int,
    /** `zuhause-text-tertiary` */
    @param:ColorInt val tertiaryText: Int,
    /** `divider-color` */
    @param:ColorInt val divider: Int,
    /** `primary-color` */
    @param:ColorInt val accent: Int,
    /** `text-primary-color`, text and icons on the accent */
    @param:ColorInt val onAccent: Int,
    /** `zuhause-accent-soft`, the tint behind today's row of the week */
    @param:ColorInt val accentSoft: Int,
    /** `zuhause-calendar-holiday`, a public holiday's day number */
    @param:ColorInt val holiday: Int,
    /** `zuhause-sheet-shadow`, the shadow under "+" */
    @param:ColorInt val shadow: Int,
    /** The fixed colours of calendars (`zuhause-calendar-*`), one for each variable of [CalendarVariables]. */
    val calendars: Map<String, Int>,
) {
    init {
        require(calendars.keys == CalendarVariables.all.toSet()) {
            "A palette has a colour for each calendar variable, ${CalendarVariables.all}, not ${calendars.keys}"
        }
    }

    /**
     * The colour an entry draws in (D49): the dashboard's, in this mode;
     * without it, the entry's own colour, else its calendar's by
     * [CalendarVariables], else the secondary text colour.
     */
    @ColorInt
    fun colorOf(entry: Entry): Int =
        entry.dashboardColor?.let(::colorOf)
            ?: entry.color
            ?: CalendarVariables.of(entry.calendar)?.let(calendars::get)
            ?: secondaryText

    @ColorInt
    private fun colorOf(color: DashboardColor): Int =
        when (color) {
            is DashboardColor.Fixed -> color.argb
            is DashboardColor.ThemeVariable -> calendars.getValue(color.name)
            DashboardColor.SecondaryText -> secondaryText
        }
}
