package dev.cupr.calendar.render

/**
 * The month's measures at phone size, in CSS px (dp on the phone), from the
 * dashboard's calendar page (`.s-phone .mg` and the rules after it in the
 * `calendar_page` styles of dashboards/zuhause.yaml, as the approved
 * mockup Month.dc.html uses them).
 */
internal object PhoneMonth {
    /** `--pt`: space above a cell's number. */
    const val CELL_TOP = 3f

    /** `.dc` padding at the sides and the bottom. */
    const val CELL_SIDE = 2f
    const val CELL_BOTTOM = 4f

    /** `--nh`: the number row. */
    const val NUMBER_ROW = 22f

    /** `--lh`: one lane of bars. */
    const val LANE = 15f

    /** `--ch`: a cell's least height. */
    const val CELL_MIN = 66f

    /** The gap between a cell's rows (`.dc` gap). */
    const val GAP = 2f

    /** `--fs` and its line height (1.45). */
    const val FONT = 10f
    const val LINE = FONT * 1.45f

    /** `--dw`: a special day's drawing beside the number. */
    const val DRAWING = 17f

    /** `--iw`: a timed entry's mark (drawing, icon or the room of the dot). */
    const val MARK = 11f

    /** A timed entry's dot without an icon. */
    const val DOT = 5f

    /** `.num` at phone size. */
    const val NUMBER_FONT = 12f
    const val NUMBER_HEIGHT = 20f
    const val NUMBER_MIN_WIDTH = 20f
    const val NUMBER_SIDE = 3f
    const val NUMBER_GAP = 3f
    const val DRAWINGS_GAP = 1f

    /** `.ev` at phone size: corner, gap, the all-day chip's padding and inset band, the timed line's padding. */
    const val ENTRY_RADIUS = 3f
    const val ENTRY_GAP = 2f
    const val CHIP_LEFT = 4f
    const val CHIP_RIGHT = 2f
    const val CHIP_BAND = 2f
    const val CHIP_DRAWING_SHIFT = -2f
    const val TIMED_SIDE = 1f

    /** `.more` */
    const val MORE_LEFT = 4f

    /** `.bar` at phone size. */
    const val BAR_INSET = 3f
    const val BAR_HEIGHT = LANE - 3f
    const val BAR_FONT = 9.5f
    const val BAR_SIDE = 3f
    const val BAR_OPEN_LEFT = 8f
    const val BAR_BAND = 3f

    /** Opacities: another month's day, a past day's entries, the weekend tint, a chip's and a bar's tint. */
    const val OTHER_MONTH = 0.38f
    const val PAST_ENTRY = 0.6f
    const val WEEKEND_TINT = 0.025f
    const val CHIP_TINT = 0.24f
    const val BAR_TINT = 0.34f

    /** CSS font weights. */
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMI_BOLD = 600
    const val BOLD = 700
}
