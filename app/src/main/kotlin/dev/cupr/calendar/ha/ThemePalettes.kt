package dev.cupr.calendar.ha

import androidx.annotation.ColorInt
import dev.cupr.calendar.model.CalendarVariables
import dev.cupr.calendar.model.Palette
import dev.cupr.calendar.model.Palettes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * The theme "Zuhause" as `frontend/get_themes` delivers it (D17), read into
 * the widgets' palettes. Each mode's variables are under `modes.light` and
 * `modes.dark`; a variable a mode does not set is the theme's own at its
 * top level, as the frontend applies them. A variable the theme does not
 * have is the built-in palette's ([Palettes.BUILT_IN]) and named in
 * [Read.Readable.missing]. A read variable whose value is no colour the app
 * reads, or `modes` or a mode that is no object, makes the theme unreadable.
 */
object ThemePalettes {
    sealed interface Read {
        /** The palettes, and the variables the theme lacks, as "light primary-color". */
        data class Readable(
            val palettes: Palettes,
            val missing: List<String>,
        ) : Read

        /** Why the theme cannot be read: the variable and its value, a theme's value, never a secret. */
        data class Unreadable(
            val reason: String,
        ) : Read
    }

    fun read(theme: JsonObject): Read {
        val missing = mutableListOf<String>()
        return try {
            val modes = objectOrNull(theme["modes"], "modes")
            Read.Readable(
                Palettes(
                    light = palette(Mode(LIGHT, theme, modes, missing), Palettes.BUILT_IN.light),
                    dark = palette(Mode(DARK, theme, modes, missing), Palettes.BUILT_IN.dark),
                ),
                missing,
            )
        } catch (e: UnreadableThemeException) {
            Read.Unreadable(e.message.orEmpty())
        }
    }

    private fun palette(
        mode: Mode,
        default: Palette,
    ) = Palette(
        card = mode.color("card-background-color", default.card),
        cardBorder = mode.color("ha-card-border-color", default.cardBorder),
        text = mode.color("primary-text-color", default.text),
        secondaryText = mode.color("secondary-text-color", default.secondaryText),
        tertiaryText = mode.color("zuhause-text-tertiary", default.tertiaryText),
        divider = mode.color("divider-color", default.divider),
        accent = mode.color("primary-color", default.accent),
        onAccent = mode.color("text-primary-color", default.onAccent),
        accentSoft = mode.color("zuhause-accent-soft", default.accentSoft),
        holiday = mode.color("zuhause-calendar-holiday", default.holiday),
        shadow = mode.color("zuhause-sheet-shadow", default.shadow),
        calendars = CalendarVariables.all.associateWith { mode.color(it, default.calendars.getValue(it)) },
    )

    /** One mode's variables: its own, else the theme's top level. */
    private class Mode(
        private val name: String,
        private val theme: JsonObject,
        modes: JsonObject?,
        private val missing: MutableList<String>,
    ) {
        private val own = objectOrNull(modes?.get(name), "modes.$name")

        @ColorInt
        fun color(
            variable: String,
            @ColorInt default: Int,
        ): Int {
            val value = own?.get(variable) ?: theme[variable]
            if (value == null) {
                missing += "$name $variable"
                return default
            }
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            return text?.let(::parseColor) ?: throw UnreadableThemeException("$name $variable is not a colour: $value")
        }
    }

    private fun objectOrNull(
        element: Any?,
        name: String,
    ): JsonObject? =
        when (element) {
            null -> null
            is JsonObject -> element
            else -> throw UnreadableThemeException("$name is not an object")
        }

    /**
     * A colour as a theme writes it: a CSS colour name, `#rgb` or `#rrggbb`
     * ([CssColors]), or `rgb(r, g, b)` or `rgba(r, g, b, a)` with an opacity
     * from 0 to 1; null for anything else.
     */
    @ColorInt
    internal fun parseColor(text: String): Int? = CssColors.parse(text) ?: rgb(text.trim())

    @ColorInt
    private fun rgb(text: String): Int? =
        RGB.matchEntire(text)?.groupValues?.let { groups ->
            val (red, green, blue) = groups.subList(1, ALPHA).map { it.toInt() }
            val opacity = groups[ALPHA].ifEmpty { null }?.toDouble() ?: 1.0
            val alpha = (opacity * MAX).roundToInt()
            ((alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue)
                .takeIf { listOf(red, green, blue).all { it <= MAX } && opacity <= 1.0 }
        }

    private class UnreadableThemeException(
        message: String,
    ) : Exception(message)

    private const val LIGHT = "light"
    private const val DARK = "dark"
    private const val MAX = 255
    private const val ALPHA = 4
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private val RGB = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*(\d*\.?\d+)\s*)?\)""")
}
