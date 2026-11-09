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
    /** `zuhause-calendar-holiday`, a public holiday's day number */
    @param:ColorInt val holiday: Int,
    /** `zuhause-sheet-shadow`, the shadow under "+" */
    @param:ColorInt val shadow: Int,
    /** The fixed colours of calendars (`zuhause-calendar-*`), by calendar. */
    val calendars: Map<String, Int>,
) {
    /** An entry's own colour, else its calendar's, else the secondary text colour. */
    @ColorInt
    fun colorOf(entry: Entry): Int = entry.color ?: calendars[entry.calendar] ?: secondaryText
}
