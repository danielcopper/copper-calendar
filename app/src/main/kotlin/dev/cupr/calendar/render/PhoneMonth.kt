package dev.cupr.calendar.render

/**
 * The month widget's measures at phone size, in dp, copied from the Home
 * Assistant dashboard so the widget looks like its calendar page.
 *
 * This is the one place the code names the dashboard's files, all in homelab
 * at 24b4a670:
 * - `infra/homeassistant/config/dashboards/zuhause.yaml`: the calendar page's
 *   month styles at phone size (these measures) and the rules MonthLayout
 *   ports;
 * - `infra/homeassistant/config/themes/zuhause.yaml`: the colours in
 *   SampleData;
 * - `infra/homeassistant/config/www/zuhause/events/`: the drawings in
 *   assets/drawings, unchanged copies;
 *
 * and at 64054110, where the templates moved from zuhause.yaml:
 * - `infra/homeassistant/config/dashboards/templates.yaml`: the tile
 *   "Kalender · diese Woche" (template `calendar`), its styles ([TileWeek]'s
 *   measures) and its rule `calendar_days`, which WeekLayout ports.
 */
internal object PhoneMonth {
    /** The card around the widget: its border, its padding at the left and right, and at the top and bottom. */
    const val CARD_BORDER = 1f
    const val CARD_SIDE = 12f
    const val CARD_END = 10f

    /** "+": its size, how far it sits from the card's inner edge (right and bottom), and its icon. */
    const val FAB_SIZE = 40f
    const val FAB_INSET = 12f
    const val FAB_ICON = 24f

    /** The card's border and side padding together: how far the drawn rows stand from the widget's edge. */
    const val CARD_INSET = CARD_BORDER + CARD_SIDE

    /** The room around "+" up to the widget's edge, where its shadow may show. */
    const val FAB_MARGIN = FAB_INSET + CARD_BORDER

    /** "+"'s shadow falls 4 dp down with a 14 dp blur. */
    const val FAB_SHADOW_Y = 4f
    const val FAB_SHADOW_BLUR = 14f

    /** The header's title row: space above, its height, space below; the title's text size, its gap to the chevron. */
    const val TITLE_TOP = 2f
    const val TITLE_ROW = 32f
    const val TITLE_BOTTOM = 4f
    const val TITLE_FONT = 13f
    const val TITLE_GAP = 8f

    /** The chevron after the title. */
    const val CHEVRON = 16f

    /** The weekday heads: text size and space below. */
    const val HEAD_FONT = 10.5f
    const val HEADS_BOTTOM = 6f

    /** The title's and the weekday heads' letter spacing, in em. */
    const val LETTER_SPACING = 0.06f

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

    /** A marked entry's mark beside the day's number (its drawing, icon or dot). */
    const val DRAWING = 17f

    /**
     * A timed entry's mark, and the mark of an all-day chip that names a
     * drawing: the drawing, the icon, or the room the dot is centred in.
     */
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
}
