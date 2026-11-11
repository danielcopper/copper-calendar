package dev.cupr.calendar.render

/**
 * The week rows' measures ("Woche"), in dp, copied from the Home Assistant
 * dashboard's tile "Kalender · diese Woche" at its default size (its file is
 * named in [PhoneMonth]). The card, header and "+" are the month widget's.
 */
internal object TileWeek {
    /** A row's padding above and below its content; a 1 dp divider stands above it. */
    const val ROW_PAD = 5f

    /** The column of the weekday and date at the left, and the gap to the entries. */
    const val DAY_COLUMN = 44f
    const val COLUMN_GAP = 8f

    /** The weekday and the date beside it: their line, text sizes, gap and the weekday's letter spacing (em). */
    const val HEAD_LINE = 20f
    const val WEEKDAY_FONT = 11f
    const val DATE_FONT = 13f
    const val HEAD_GAP = 4f
    const val WEEKDAY_SPACING = 0.04f

    /** Today's weekday ("Heute") stands above its date, each on a line of this height. */
    const val TODAY_LINE = 16f

    /** Today's tint reaches this far out to the left of the row, fading in over that width. */
    const val TODAY_REACH = 12f

    /** Today's tint fades out at this share of its width, and its corner radius. */
    const val TODAY_FADE = 0.7f
    const val TODAY_RADIUS = 6f

    /** An entry's line: its height, the gap between lines, its text size, and the gap between its parts. */
    const val LINE = 20f
    const val LINE_GAP = 1f
    const val FONT = 13f
    const val PART_GAP = 6f

    /** The bar in the entry's colour at the left of each line: its width and corner radius. */
    const val BAR = 3f
    const val BAR_RADIUS = 1.5f

    /** A timed line: the time's column, the mark, the dot drawn without an icon. */
    const val TIME = 76f
    const val MARK = 14f
    const val DOT = 7f

    /** An all-day chip: text size, padding left and right, mark, gap after it, radius, tint. */
    const val CHIP_FONT = 12f
    const val CHIP_LEFT = 6f
    const val CHIP_RIGHT = 8f
    const val CHIP_MARK = 16f
    const val CHIP_GAP = 5f
    const val CHIP_RADIUS = 6f
    const val CHIP_TINT = 0.22f

    /** A day before today is drawn at this opacity. */
    const val PAST = 0.5f
}
