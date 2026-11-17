package dev.cupr.calendar.widget

import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.week.WeekDays
import java.time.Duration
import java.time.Instant

/**
 * What one drawing of a widget takes from its settings and its redraw: the
 * days a week widget shows (D20), the corner of "+", and whether the month's
 * or "Woche"'s list scrolls to today ([ReturnToToday]). The calendars it
 * leaves out are gone from the calendar it draws ([Content.Calendar.without]).
 */
data class Look(
    val days: WeekDays = WeekDays.MONDAY_TO_SUNDAY,
    val corner: PlusCorner = PlusCorner.BOTTOM_RIGHT,
    val scroll: Boolean = true,
) {
    companion object {
        /** The look of a widget with settings, scrolling to today or not. */
        internal fun of(
            settings: WidgetSettings,
            scroll: Boolean,
        ) = Look(settings.days, settings.plusCorner, scroll)
    }
}

/** Why a widget is drawn anew, which decides whether its list returns to today ([ReturnToToday]). */
enum class Redraw {
    /**
     * An update: after a fetch, a new sign-in or signing out ([Widgets.update]),
     * or the launcher's own, every 30 minutes and for a new or resized widget.
     */
    UPDATE,

    /** The update at midnight ([Midnight]). */
    MIDNIGHT,

    /** The widget's settings were just saved in its setup. */
    SAVED,
}

/**
 * Whether a redraw brings today's row to the top of the month's or
 * "Woche"'s list, by the widget's "Zurück zu heute" (D27). A redraw that
 * does not leaves the list where the user scrolled it: the launcher applies
 * the new views to the list it shows, which keeps its position (measured on
 * the phone, 2026-11-17). Saving the settings always scrolls, so the widget
 * shows at once what it was set to (D59).
 */
internal object ReturnToToday {
    /**
     * Whether redraw scrolls at now: on each update, at every redraw; only at
     * midnight, at the midnight's; never, at none; every 15, 30 or 60
     * minutes, at the first redraw once that long has passed since the list
     * last scrolled to today ([lastScroll]), or when it never has.
     */
    fun due(
        setting: BackToToday,
        redraw: Redraw,
        lastScroll: Instant?,
        now: Instant,
    ): Boolean =
        redraw == Redraw.SAVED ||
            when (setting) {
                BackToToday.EACH_UPDATE -> true

                BackToToday.MIDNIGHT -> redraw == Redraw.MIDNIGHT

                BackToToday.NEVER -> false

                BackToToday.EVERY_15_MINUTES,
                BackToToday.EVERY_30_MINUTES,
                BackToToday.EVERY_60_MINUTES,
                -> passed(lastScroll, now, Duration.ofMinutes(checkNotNull(setting.minutes)))
            }

    private fun passed(
        lastScroll: Instant?,
        now: Instant,
        interval: Duration,
    ) = lastScroll == null || Duration.between(lastScroll, now) >= interval
}
