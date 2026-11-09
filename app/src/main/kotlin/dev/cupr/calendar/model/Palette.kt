package dev.cupr.calendar.model

/**
 * The colours of one theme mode (ARGB), named after the Home Assistant theme
 * variables they come from.
 */
data class Palette(
    /** `card-background-color` */
    val card: Int,
    /** `ha-card-border-color` */
    val cardBorder: Int,
    /** `primary-text-color` */
    val text: Int,
    /** `secondary-text-color` */
    val secondaryText: Int,
    /** `zuhause-text-tertiary` */
    val tertiaryText: Int,
    /** `divider-color` */
    val divider: Int,
    /** `primary-color` */
    val accent: Int,
    /** `text-primary-color`, text and icons on the accent */
    val onAccent: Int,
    /** `zuhause-calendar-holiday`, a public holiday's day number */
    val holiday: Int,
    /** `zuhause-sheet-shadow`, the shadow under "+" */
    val shadow: Int,
    /** The fixed colours of calendars (`zuhause-calendar-*`), by calendar. */
    val calendars: Map<String, Int>,
) {
    /** An entry's own colour, else its calendar's, else the secondary text colour. */
    fun colorOf(entry: Entry): Int = entry.color ?: calendars[entry.calendar] ?: secondaryText
}
