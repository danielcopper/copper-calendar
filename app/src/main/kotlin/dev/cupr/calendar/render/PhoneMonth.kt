package dev.cupr.calendar.render

/**
 * The month's measures at phone size, in dp. They are copied from the Home
 * Assistant dashboard (homelab, `infra/homeassistant/config/dashboards/
 * zuhause.yaml`: the calendar page's month styles at phone size), so the
 * widget looks like the page; its colours come from `themes/zuhause.yaml`
 * (see SampleData). [ChromeRenderer]'s measures come from the same page.
 */
internal object PhoneMonth {
    /** The card around the widget: its border and its padding at the left and right. */
    const val CARD_BORDER = 1f
    const val CARD_SIDE = 12f

    /** "+": how far it sits from the card's inner edge, right and bottom. */
    const val FAB_INSET = 12f

    /** Space above a cell's number. */
    const val CELL_TOP = 3f

    /** A cell's padding at the sides and the bottom. */
    const val CELL_SIDE = 2f
    const val CELL_BOTTOM = 4f

    /** The height of the row with the day's number. */
    const val NUMBER_ROW = 22f

    /** The height of one lane of bars. */
    const val LANE = 15f

    /** A cell's least height. */
    const val CELL_MIN = 66f

    /** The gap between the rows of a cell. */
    const val GAP = 2f

    /** The entries' text size and the height of an entry's line (1.45 times the text size). */
    const val FONT = 10f
    const val LINE = FONT * 1.45f

    /** A special day's drawing beside the number. */
    const val DRAWING = 17f

    /** A timed entry's mark: its drawing or icon, or the room the dot is centred in. */
    const val MARK = 11f

    /** A timed entry's dot without an icon. */
    const val DOT = 5f

    /** The day's number: text size, pill height and least width, padding at its sides, gap to the drawings. */
    const val NUMBER_FONT = 12f
    const val NUMBER_HEIGHT = 20f
    const val NUMBER_MIN_WIDTH = 20f
    const val NUMBER_SIDE = 3f
    const val NUMBER_GAP = 3f
    const val DRAWINGS_GAP = 1f

    /** An entry's line: corner radius and the gap between its mark and its title. */
    const val ENTRY_RADIUS = 3f
    const val ENTRY_GAP = 2f

    /** An all-day chip: padding left and right, the coloured band at its left, its drawing pulled left. */
    const val CHIP_LEFT = 4f
    const val CHIP_RIGHT = 2f
    const val CHIP_BAND = 2f
    const val CHIP_DRAWING_SHIFT = -2f

    /** A timed line's padding at its sides. */
    const val TIMED_SIDE = 1f

    /** "+N": its space to the left. */
    const val MORE_LEFT = 4f

    /** A bar: inset from a column edge where it begins or ends, its gap to the next lane, text size, padding. */
    const val BAR_INSET = 3f
    const val BAR_LANE_GAP = 3f
    const val BAR_HEIGHT = LANE - BAR_LANE_GAP
    const val BAR_FONT = 9.5f
    const val BAR_SIDE = 3f

    /** A bar where it begins: wider left padding beside its coloured band. */
    const val BAR_OPEN_LEFT = 8f
    const val BAR_BAND = 3f

    /** Opacities: another month's day, a past day's entries, the weekend tint, a chip's and a bar's tint. */
    const val OTHER_MONTH = 0.38f
    const val PAST_ENTRY = 0.6f
    const val WEEKEND_TINT = 0.025f
    const val CHIP_TINT = 0.24f
    const val BAR_TINT = 0.34f

    /** Font weights. */
    const val REGULAR = 400
    const val MEDIUM = 500
    const val SEMI_BOLD = 600
    const val BOLD = 700
}
