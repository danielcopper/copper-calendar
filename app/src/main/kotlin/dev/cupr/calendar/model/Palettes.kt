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
        /**
         * The calendars with a fixed colour in the theme, by the id an entry
         * names them with, and the theme variable of each: the dashboard's
         * `calendar_colors` (its file is named in
         * [dev.cupr.calendar.render.PhoneMonth]).
         */
        val CALENDAR_VARIABLES: Map<String, String> =
            mapOf(
                "calendar.contact_birthdays" to "zuhause-calendar-birthdays",
                "calendar.schulferien_hessen" to "zuhause-calendar-school",
                "calendar.deutschland_he" to "zuhause-calendar-public",
                "special" to "zuhause-calendar-special",
            )

        private val calendarsDark =
            mapOf(
                "zuhause-calendar-birthdays" to 0xFFFFD700.toInt(),
                "zuhause-calendar-school" to 0xFF40E0D0.toInt(),
                "zuhause-calendar-public" to 0xFFA9A9A9.toInt(),
                "zuhause-calendar-special" to 0xFFFF8C00.toInt(),
            )

        private val calendarsLight =
            mapOf(
                "zuhause-calendar-birthdays" to 0xFFA38A00.toInt(),
                "zuhause-calendar-school" to 0xFF2D9D92.toInt(),
                "zuhause-calendar-public" to 0xFF8C8C8C.toInt(),
                "zuhause-calendar-special" to 0xFFD67500.toInt(),
            )

        /**
         * The built-in palettes: the theme "Zuhause"'s values, copied from
         * homelab's theme file (named in [dev.cupr.calendar.render.PhoneMonth]).
         * They stand in for a theme the app does not have or cannot read, and
         * each of their colours for a variable the theme lacks (D17). The
         * card, its border and the plain text are Android resources of the
         * same values (`values/colors.xml`, `values-night/colors.xml`).
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
                        calendars = CALENDAR_VARIABLES.mapValues { (_, variable) -> calendarsLight.getValue(variable) },
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
                        calendars = CALENDAR_VARIABLES.mapValues { (_, variable) -> calendarsDark.getValue(variable) },
                    ),
            )
    }
}
