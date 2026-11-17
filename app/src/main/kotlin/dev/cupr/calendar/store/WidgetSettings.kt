package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.cupr.calendar.week.WeekDays

/** When the month and "Woche" return to today, "Zurück zu heute" (D27); [minutes] for the intervals. */
enum class BackToToday(
    override val stored: String,
    val minutes: Long? = null,
) : StoredChoice {
    EACH_UPDATE("each_update"),
    MIDNIGHT("midnight"),
    NEVER("never"),
    EVERY_15_MINUTES("every_15", minutes = 15),
    EVERY_30_MINUTES("every_30", minutes = 30),
    EVERY_60_MINUTES("every_60", minutes = 60),
}

/** The corner "+" stands in; the top corners hold the header. */
enum class PlusCorner(
    override val stored: String,
) : StoredChoice {
    BOTTOM_RIGHT("bottom_right"),
    BOTTOM_LEFT("bottom_left"),
}

/**
 * One widget's settings from its setup: the calendars it leaves out, so a
 * calendar that comes later shows (D11), its days (D20, the week widgets),
 * when it returns to today (D27, the month and "Woche"), and the corner of
 * "+". Each defaults to what a widget without settings shows.
 */
data class WidgetSettings(
    val leftOut: Set<String> = emptySet(),
    val days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
    val backToToday: BackToToday = BackToToday.EACH_UPDATE,
    val plusCorner: PlusCorner = PlusCorner.BOTTOM_RIGHT,
) {
    companion object {
        val DEFAULT = WidgetSettings()
    }
}

/**
 * Keeps each widget's [WidgetSettings] by its id until the widget is
 * removed; a widget with none stored has the defaults (D57).
 */
class WidgetSettingsStore(
    private val prefs: SharedPreferences,
) {
    /** The widget's settings; a choice stored under a name this app does not know reads as its default, logged. */
    fun read(id: Int): WidgetSettings {
        val default = WidgetSettings.DEFAULT
        return WidgetSettings(
            leftOut = prefs.getStringSet(key(id, LEFT_OUT), null)?.toSet() ?: default.leftOut,
            days = choice(id, DAYS, WeekDays.entries, default.days),
            backToToday = choice(id, BACK_TO_TODAY, BackToToday.entries, default.backToToday),
            plusCorner = choice(id, PLUS_CORNER, PlusCorner.entries, default.plusCorner),
        )
    }

    fun write(
        id: Int,
        settings: WidgetSettings,
    ) = prefs.edit {
        putStringSet(key(id, LEFT_OUT), settings.leftOut)
        putString(key(id, DAYS), settings.days.stored)
        putString(key(id, BACK_TO_TODAY), settings.backToToday.stored)
        putString(key(id, PLUS_CORNER), settings.plusCorner.stored)
    }

    /** Forgets the settings of the widgets ids, removed from the home screen. */
    fun remove(ids: IntArray) =
        prefs.edit {
            for (id in ids) {
                FIELDS.forEach { remove(key(id, it)) }
            }
        }

    private fun <T : StoredChoice> choice(
        id: Int,
        field: String,
        entries: List<T>,
        default: T,
    ): T = prefs.choice(key(id, field), entries, default, TAG, "Widget $id: its stored $field")

    private companion object {
        const val TAG = "WidgetSettings"
        const val LEFT_OUT = "left_out"
        const val DAYS = "days"
        const val BACK_TO_TODAY = "back_to_today"
        const val PLUS_CORNER = "plus_corner"
        val FIELDS = listOf(LEFT_OUT, DAYS, BACK_TO_TODAY, PLUS_CORNER)

        fun key(
            id: Int,
            field: String,
        ) = "$id.$field"
    }
}
