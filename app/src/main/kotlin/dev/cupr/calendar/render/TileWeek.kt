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

    /** A line of text: its height, the gap between entries' rows, its text size, the gap between a row's parts. */
    const val LINE = 20f
    const val LINE_GAP = 2f
    const val FONT = 13f
    const val PART_GAP = 6f

    /** An entry's row around its line: its padding above and below, its padding at its right end, its corner radius. */
    const val ENTRY_PAD = 1f
    const val ENTRY_END = 8f
    const val ENTRY_RADIUS = 6f

    /** An entry's row is tinted in its colour: a timed entry's faintly, an all-day entry's more. */
    const val TIMED_TINT = 0.06f
    const val ALL_DAY_TINT = 0.12f

    /**
     * The bar in the entry's colour at the left of its row: its width, and
     * a timed row's corner radius; an all-day row's bar is square and as
     * tall as the row.
     */
    const val BAR = 3f
    const val BAR_RADIUS = 1.5f

    /** The mark, and the dot a timed row draws without an icon or drawing. */
    const val MARK = 14f
    const val DOT = 7f

    /** The time at a row's right end: its text size, and its padding at its left, beyond the gap between parts. */
    const val TIME_FONT = 12f
    const val TIME_GAP = 8f

    /** A day before today is drawn at this opacity. */
    const val PAST = 0.5f

    /** A timed entry of today whose end has passed is drawn at this opacity. */
    const val OVER = 0.5f
}
