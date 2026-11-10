package dev.cupr.calendar.tap

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** What a tap on a widget hit (design.md, "Taps"). */
enum class TapElement { HEADER, DAY, ENTRY, NEW }

/** A tap on a widget: its element and, where the element has one, its day. */
sealed interface Tap {
    val element: TapElement
    val day: LocalDate?

    /** The header: no day. */
    data object Header : Tap {
        override val element = TapElement.HEADER
        override val day: LocalDate? = null
    }

    /** A day of the month or the week. */
    data class Day(
        override val day: LocalDate,
    ) : Tap {
        override val element = TapElement.DAY
    }

    /** An entry of the week's rows, by its UID, on the day it stands on. */
    data class Entry(
        val uid: String,
        override val day: LocalDate,
    ) : Tap {
        override val element = TapElement.ENTRY
    }

    /** "+", with the day a new entry starts on (today). */
    data class New(
        override val day: LocalDate,
    ) : Tap {
        override val element = TapElement.NEW
    }
}

/**
 * The tap targets (design.md, "Taps"): a template per element, a Home
 * Assistant path opened through the Home Assistant app, with `{day}` for the
 * tap's day. The defaults open the calendar page of the dashboard "Zuhause";
 * targets become configurable per widget later (D23).
 */
object TapTargets {
    /** The scheme of the links the Home Assistant app opens. */
    const val HOME_ASSISTANT_SCHEME = "homeassistant"

    private const val CALENDAR = "$HOME_ASSISTANT_SCHEME://navigate/dashboard-zuhause/kalender"
    private const val DAY_SLOT = "{day}"
    private const val UID_SLOT = "{uid}"

    val defaults: Map<TapElement, String> =
        mapOf(
            TapElement.HEADER to CALENDAR,
            TapElement.DAY to "$CALENDAR?day=$DAY_SLOT",
            TapElement.ENTRY to "$CALENDAR?entry=$UID_SLOT&day=$DAY_SLOT",
            TapElement.NEW to "$CALENDAR?new=$DAY_SLOT",
        )

    /**
     * The target of a tap: its element's template with its day filled in, and
     * an entry's UID, encoded for a URI, since a UID may hold any character.
     */
    fun uri(
        tap: Tap,
        template: String = defaults.getValue(tap.element),
    ): String {
        val withUid = if (tap is Tap.Entry) template.replace(UID_SLOT, Uri.encode(tap.uid)) else template
        return tap.day?.let { withUid.replace(DAY_SLOT, it.toString()) } ?: withUid
    }
}

/**
 * The intents a widget's taps send to [TapActivity]. The request codes are
 * one per element; once targets are configurable per widget (D23) they have
 * to include the widget's id, or two widgets' intents replace each other.
 */
object Taps {
    private const val ELEMENT = "dev.cupr.calendar.tap.ELEMENT"
    private const val DAY = "dev.cupr.calendar.tap.DAY"
    private const val UID = "dev.cupr.calendar.tap.UID"
    private const val TEMPLATE_REQUEST = 100

    /** The extras of a tap: alone, the fill-in of a list row's cell. */
    fun fillIn(tap: Tap): Intent {
        val intent = Intent().putExtra(ELEMENT, tap.element.name)
        tap.day?.let { intent.putExtra(DAY, it.toString()) }
        if (tap is Tap.Entry) intent.putExtra(UID, tap.uid)
        return intent
    }

    /** A tap on a fixed part of the widget (header, "+"). */
    fun pendingIntent(
        context: Context,
        tap: Tap,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            tap.element.ordinal,
            fillIn(tap).setClass(context, TapActivity::class.java),
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

    /**
     * The tap an intent carries, none when it carries no complete one: no or
     * an unknown element, a day, an entry or "+" without a well-formed day, or
     * an entry without a UID.
     */
    fun read(intent: Intent): Tap? {
        val element = intent.getStringExtra(ELEMENT)?.let { name -> TapElement.entries.find { it.name == name } }
        val day = intent.getStringExtra(DAY)?.let(::parseDay)
        return when (element) {
            TapElement.HEADER -> Tap.Header
            TapElement.DAY -> day?.let(Tap::Day)
            TapElement.ENTRY -> entry(intent.getStringExtra(UID), day)
            TapElement.NEW -> day?.let(Tap::New)
            null -> null
        }
    }

    private fun entry(
        uid: String?,
        day: LocalDate?,
    ): Tap? = if (uid.isNullOrEmpty() || day == null) null else Tap.Entry(uid, day)

    /** What an intent carries, for a log line: its element and day as given, and the keys of all its extras. */
    fun describe(intent: Intent): String =
        "element=${intent.getStringExtra(ELEMENT)}, day=${intent.getStringExtra(DAY)}, " +
            "extras: ${intent.extras?.keySet().orEmpty().sorted()}"

    private fun parseDay(text: String): LocalDate? =
        try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
}
