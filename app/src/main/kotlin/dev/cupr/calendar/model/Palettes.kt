package dev.cupr.calendar.model

/**
 * The palettes the widgets draw with, light and dark: the theme "Zuhause"
 * as Home Assistant delivers it (D17), or [BUILT_IN] without one.
 */
data class Palettes(
    val light: Palette,
    val dark: Palette,
) {
    companion object {
        private val calendarsDark =
            mapOf(
                CalendarVariables.FAMILY to 0xFF3CB371.toInt(),
                CalendarVariables.BIRTHDAYS to 0xFFFFD700.toInt(),
                CalendarVariables.SCHOOL to 0xFF40E0D0.toInt(),
                CalendarVariables.PUBLIC to 0xFFA9A9A9.toInt(),
                CalendarVariables.SPECIAL to 0xFFFF8C00.toInt(),
            )

        private val calendarsLight =
            mapOf(
                CalendarVariables.FAMILY to 0xFF359F65.toInt(),
                CalendarVariables.BIRTHDAYS to 0xFFA38A00.toInt(),
                CalendarVariables.SCHOOL to 0xFF2D9D92.toInt(),
                CalendarVariables.PUBLIC to 0xFF8C8C8C.toInt(),
                CalendarVariables.SPECIAL to 0xFFD67500.toInt(),
            )

        /**
         * The built-in palettes: the theme "Zuhause"'s values, copied from
         * homelab's theme file (named in [dev.cupr.calendar.render.PhoneMonth]).
         * They stand in for a theme the app does not have or cannot read, and
         * each of their colours for a variable the theme lacks (D17). Some of
         * the widgets' parts are Android resources of these values
         * (`values/colors.xml`, `values-night/colors.xml`), which the theme
         * does not change (D48).
         */
        val BUILT_IN =
            Palettes(
                light =
                    Palette(
                        card = 0xFFFFFFFF.toInt(),
                        cardBorder = 0xFFDDE2E9.toInt(),
                        text = 0xFF161B22.toInt(),
                        secondaryText = 0xFF5B6573.toInt(),
                        tertiaryText = 0xFF666E7A.toInt(),
                        divider = 0xFFE6EAF0.toInt(),
                        accent = 0xFF1F6FD6.toInt(),
                        onAccent = 0xFFFFFFFF.toInt(),
                        // The accent at 12 % opacity.
                        accentSoft = 0x1F1F6FD6,
                        holiday = 0xFFB52222.toInt(),
                        // The light text colour at 18 % opacity.
                        shadow = 0x2E161B22,
                        calendars = calendarsLight,
                    ),
                dark =
                    Palette(
                        card = 0xFF1A1F25.toInt(),
                        cardBorder = 0xFF262C34.toInt(),
                        text = 0xFFECEEF1.toInt(),
                        secondaryText = 0xFFA3ACB7.toInt(),
                        tertiaryText = 0xFF7D8793.toInt(),
                        divider = 0xFF262C34.toInt(),
                        accent = 0xFF6FB1FF.toInt(),
                        onAccent = 0xFF0D1A2A.toInt(),
                        // The accent at 16 % opacity.
                        accentSoft = 0x296FB1FF,
                        holiday = 0xFFFF7A7A.toInt(),
                        // Black at half opacity.
                        shadow = 0x80000000.toInt(),
                        calendars = calendarsDark,
                    ),
            )
    }
}

/**
 * Which theme variable colours the entries of a calendar that have no own
 * colour (D16, D49): the dashboard's `calendar_colors` (its file is named in
 * [dev.cupr.calendar.render.PhoneMonth]), with the family calendar by the
 * start of its id, so no person's id is written here. The dashboard takes a
 * calendar's colour in Nextcloud first, which the app never receives; a
 * calendar not named here draws in the secondary text colour.
 */
object CalendarVariables {
    const val FAMILY = "zuhause-calendar-family"
    const val BIRTHDAYS = "zuhause-calendar-birthdays"
    const val SCHOOL = "zuhause-calendar-school"
    const val PUBLIC = "zuhause-calendar-public"
    const val SPECIAL = "zuhause-calendar-special"

    private const val FAMILY_PREFIX = "calendar.family_"

    private val fixed =
        mapOf(
            "calendar.contact_birthdays" to BIRTHDAYS,
            "calendar.schulferien_hessen" to SCHOOL,
            "calendar.deutschland_he" to PUBLIC,
            "special" to SPECIAL,
        )

    /** Every variable a calendar can take its colour from. */
    val all: List<String> = listOf(FAMILY, BIRTHDAYS, SCHOOL, PUBLIC, SPECIAL)

    /** The variable of calendar, an entry's calendar id; none for a calendar without a fixed colour. */
    fun of(calendar: String): String? = fixed[calendar] ?: FAMILY.takeIf { calendar.startsWith(FAMILY_PREFIX) }
}
