package dev.cupr.calendar.tap

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/** What a tap on a widget hit (design.md, "Taps"). */
enum class TapElement { HEADER, DAY, NEW }

/**
 * The default tap targets (design.md, "Taps"): the calendar page of the
 * dashboard "Zuhause" in the Home Assistant app, with the tapped day for a
 * day and today for "+". Targets become configurable per widget later (D23).
 */
object TapTargets {
    private const val CALENDAR = "homeassistant://navigate/dashboard-zuhause/kalender"

    fun uri(
        element: TapElement,
        day: LocalDate,
    ): String =
        when (element) {
            TapElement.HEADER -> CALENDAR
            TapElement.DAY -> "$CALENDAR?day=$day"
            TapElement.NEW -> "$CALENDAR?new=$day"
        }
}

/** The intents a widget's taps send to [TapActivity]. */
object Taps {
    private const val ELEMENT = "dev.cupr.calendar.tap.ELEMENT"
    private const val DAY = "dev.cupr.calendar.tap.DAY"

    /** The extras of a tap on element for day: alone, the fill-in of a list row's cell. */
    fun fillIn(
        element: TapElement,
        day: LocalDate,
    ): Intent = Intent().putExtra(ELEMENT, element.name).putExtra(DAY, day.toString())

    /** A tap on a fixed part of the widget (header, "+"). */
    fun pendingIntent(
        context: Context,
        element: TapElement,
        day: LocalDate,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            element.ordinal,
            fillIn(element, day).setClass(context, TapActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The template the cells of the list's rows fill in; mutable, so the fill-in can add the extras. */
    fun template(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            TEMPLATE_REQUEST,
            Intent(context, TapActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The tap an intent carries, none when it carries no complete one. */
    fun read(intent: Intent): Pair<TapElement, LocalDate>? {
        val element = intent.getStringExtra(ELEMENT)?.let { name -> TapElement.entries.find { it.name == name } }
        val day = intent.getStringExtra(DAY)?.let(LocalDate::parse)
        return if (element != null && day != null) element to day else null
    }

    private const val TEMPLATE_REQUEST = 100
}
