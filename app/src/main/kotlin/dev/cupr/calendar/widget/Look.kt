package dev.cupr.calendar.widget

import dev.cupr.calendar.store.BackToToday
import dev.cupr.calendar.store.ListState
import dev.cupr.calendar.store.ListStore
import dev.cupr.calendar.store.PlusCorner
import dev.cupr.calendar.store.TapTargets
import dev.cupr.calendar.store.WidgetSettings
import dev.cupr.calendar.week.WeekDays
import java.time.Duration
import java.time.Instant

/**
 * What one drawing of a widget takes from its settings and its redraw: the
 * days a week widget shows (D20), the corner of "+" ([Plus]), whether the
 * month's or "Woche"'s list scrolls to today ([ReturnToToday]), its tap
 * targets, of which an element set to open nothing gets no tap, and "+" set
 * so is hidden (D63), and the card's opacity in percent ([Card], D64). The
 * calendars it leaves out are gone from the calendar it draws
 * ([Content.Calendar.without]).
 */
data class Look(
    val days: WeekDays = WidgetSettings.DEFAULT.days,
    val corner: PlusCorner = WidgetSettings.DEFAULT.plusCorner,
    val scroll: Boolean = true,
    val taps: TapTargets = WidgetSettings.DEFAULT.taps,
    val opacity: Int = WidgetSettings.DEFAULT.opacity,
) {
    companion object {
        /** The look of a widget with settings, scrolling to today or not. */
        internal fun of(
            settings: WidgetSettings,
            scroll: Boolean,
        ) = Look(settings.days, settings.plusCorner, scroll, settings.taps, settings.opacity)
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
 * "Woche"'s list, by the widget's "Zurück zu heute" (D27); a redraw that
 * does not leaves the list where the user scrolled it (D59). Saving the
 * settings scrolls whatever they say (D59).
 */
internal object ReturnToToday {
    /**
     * Whether redraw scrolls at now: on each update, at every redraw; only at
     * midnight, at the midnight's; never, at none; every 15, 30 or 60
     * minutes, at the first redraw once that long has passed since the list
     * last scrolled to today ([lastScroll]), or when it never has, or when
     * that scroll is after now, the clock set back since.
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

    /** One update of a list ([step]): whether it scrolls to today, and where the list stands after it. */
    data class Step(
        val scroll: Boolean,
        val next: ListState,
    ) {
        /**
         * Whether the scroll counts as the list's last one
         * ([ListStore.lastScroll]): one that leaves the list settled; the
         * scroll of the update that builds the list does nothing (D27).
         */
        val counts: Boolean get() = scroll && next == ListState.SETTLED
    }

    /**
     * The update of the month's or "Woche"'s list at now for redraw, from
     * where the list stands (D59): the update that builds the list anew
     * scrolls as [due] says, but its scroll does nothing (D27) and does not
     * count; the update after it scrolls to today whatever the setting, and
     * counts; from then on [due] decides, and a scroll counts.
     */
    fun step(
        setting: BackToToday,
        redraw: Redraw,
        state: ListState,
        lastScroll: Instant?,
        now: Instant,
    ): Step =
        when (state) {
            ListState.TO_BUILD -> Step(due(setting, redraw, lastScroll, now), ListState.TO_SCROLL)
            ListState.TO_SCROLL -> Step(scroll = true, ListState.SETTLED)
            ListState.SETTLED -> Step(due(setting, redraw, lastScroll, now), ListState.SETTLED)
        }

    private fun passed(
        lastScroll: Instant?,
        now: Instant,
        interval: Duration,
    ) = lastScroll == null || lastScroll.isAfter(now) || Duration.between(lastScroll, now) >= interval
}
