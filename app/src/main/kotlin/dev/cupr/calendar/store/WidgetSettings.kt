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

/** The name a week widget's days are kept under in its settings, fixed apart from the enum's names. */
internal val WeekDays.stored: String
    get() =
        when (this) {
            WeekDays.MONDAY_TO_SUNDAY -> "monday_to_sunday"
            WeekDays.FROM_TODAY -> "from_today"
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
 * when it returns to today (D27, the month and "Woche"), the corner of
 * "+", the card's opacity ([CardOpacity], D64), and what a tap on
 * each element opens (D63). Each defaults to what a widget without settings
 * shows.
 */
data class WidgetSettings(
    val leftOut: Set<String> = emptySet(),
    val days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
    val backToToday: BackToToday = BackToToday.EACH_UPDATE,
    val plusCorner: PlusCorner = PlusCorner.BOTTOM_RIGHT,
    val taps: TapTargets = TapTargets.DEFAULT,
    val opacity: CardOpacity = CardOpacity.FULL,
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
    /**
     * The widget's settings; a choice stored under a name this app does not
     * know, or an opacity that is no number or no step of [CardOpacity],
     * reads as its default, logged.
     */
    fun read(id: Int): WidgetSettings {
        val default = WidgetSettings.DEFAULT
        return WidgetSettings(
            leftOut = prefs.getStringSet(key(id, LEFT_OUT), null)?.toSet() ?: default.leftOut,
            days = prefs.choice(key(id, DAYS), WeekDays.entries, default.days, TAG, what(id, DAYS)) { it.stored },
            backToToday = choice(id, BACK_TO_TODAY, BackToToday.entries, default.backToToday),
            plusCorner = choice(id, PLUS_CORNER, PlusCorner.entries, default.plusCorner),
            taps =
                TapElement.entries.fold(default.taps) { taps, element ->
                    taps.with(element, target(id, element, default.taps.of(element)))
                },
            opacity =
                prefs.choice(
                    key(id, OPACITY),
                    default.opacity,
                    TAG,
                    what(id, OPACITY),
                    CardOpacity::stored,
                    NO_STEP,
                    CardOpacity::ofStored,
                ),
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
        for ((element, field) in TAP_FIELDS) putString(key(id, field), settings.taps.of(element).stored)
        putString(key(id, OPACITY), settings.opacity.stored)
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
    ): T = prefs.choice(key(id, field), entries, default, TAG, what(id, field))

    /**
     * The target of element, default when none is stored; one stored under a
     * name this app does not know, or of a kind the element cannot have (an
     * entry another app, the header an entry's day), reads as default, logged.
     */
    private fun target(
        id: Int,
        element: TapElement,
        default: TapTarget,
    ): TapTarget {
        val field = TAP_FIELDS.getValue(element)
        return prefs.choice(
            key(id, field),
            default,
            TAG,
            what(id, field),
            TapTarget::stored,
            MISPLACED,
            element::target,
        )
    }

    private companion object {
        const val TAG = "WidgetSettings"
        const val LEFT_OUT = "left_out"
        const val DAYS = "days"
        const val BACK_TO_TODAY = "back_to_today"
        const val PLUS_CORNER = "plus_corner"
        const val OPACITY = "opacity"
        const val MISPLACED = "is unknown or not one this element can have"
        const val NO_STEP = "is no opacity from 0 to 100 in steps of 10"

        /** The fields of the elements' targets, by element. */
        val TAP_FIELDS =
            mapOf(
                TapElement.HEADER to "tap_header",
                TapElement.DAY to "tap_day",
                TapElement.ENTRY to "tap_entry",
                TapElement.NEW to "tap_new",
            )
        val FIELDS = listOf(LEFT_OUT, DAYS, BACK_TO_TODAY, PLUS_CORNER, OPACITY) + TAP_FIELDS.values

        fun key(
            id: Int,
            field: String,
        ) = "$id.$field"

        /** What the log names a stored field by. */
        fun what(
            id: Int,
            field: String,
        ) = "Widget $id: its stored $field"
    }
}
