package dev.cupr.calendar.tap

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.cupr.calendar.signin.SignInActivity
import dev.cupr.calendar.store.TapElement
import dev.cupr.calendar.store.TapTargets
import java.time.LocalDate
import java.time.format.DateTimeParseException

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

        init {
            require(uidOf(uid) != null) { "An entry's tap needs its UID" }
        }

        companion object {
            /** The UID uid names an entry by, none for one that is missing, empty or blank (D37). */
            fun uidOf(uid: String?): String? = uid?.takeIf { it.isNotBlank() }
        }
    }

    companion object {
        /** A tap on an entry: the entry by its UID on day, or day when it has no UID ([Entry.uidOf], D37). */
        fun entryOrDay(
            uid: String?,
            day: LocalDate,
        ): Tap = Entry.uidOf(uid)?.let { Entry(it, day) } ?: Day(day)
    }

    /** "+", with the day a new entry starts on (today). */
    data class New(
        override val day: LocalDate,
    ) : Tap {
        override val element = TapElement.NEW
    }
}

/**
 * The links into the calendar page of the dashboard "Zuhause" (design.md,
 * "Taps"): a template per element, a Home Assistant path opened through the
 * Home Assistant app, with `{day}` for the tap's day. Each element opens its
 * link unless the widget's setup gives it another target (D63).
 */
object CalendarLinks {
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
 * The intents a widget's taps send to [TapActivity], each with the widget's
 * id, by which the tap activity reads the widget's targets (D63); an intent
 * without one opens the element's calendar link whatever any widget's
 * targets, as the plain texts promise.
 */
object Taps {
    private const val ELEMENT = "dev.cupr.calendar.tap.ELEMENT"
    private const val DAY = "dev.cupr.calendar.tap.DAY"
    private const val UID = "dev.cupr.calendar.tap.UID"
    private const val WIDGET = "dev.cupr.calendar.tap.WIDGET"
    private const val TEMPLATE_REQUEST = 100
    private const val SIGN_IN_REQUEST = 101

    /** Where the request codes of the widgets' fixed parts start, past the others. */
    private const val WIDGET_REQUESTS = 1000

    /** The extras of a tap on the widget [widget], none for no widget's: alone, the fill-in of a list row's cell. */
    fun fillIn(
        tap: Tap,
        widget: Int?,
    ): Intent {
        val intent = Intent().putExtra(ELEMENT, tap.element.name)
        tap.day?.let { intent.putExtra(DAY, it.toString()) }
        if (tap is Tap.Entry) intent.putExtra(UID, tap.uid)
        widget?.let { intent.putExtra(WIDGET, it) }
        return intent
    }

    /**
     * A tap on a fixed part (header, "+") of the widget [widget], or, with
     * none, of a plain text. Its request code is one per widget and element,
     * so the intents of two widgets do not replace each other.
     */
    fun pendingIntent(
        context: Context,
        tap: Tap,
        widget: Int?,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(tap.element, widget),
            fillIn(tap, widget).setClass(context, TapActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The request code of a tap on element of the widget [widget], or of a plain text. */
    internal fun requestCode(
        element: TapElement,
        widget: Int?,
    ): Int =
        if (widget == null) {
            element.ordinal
        } else {
            WIDGET_REQUESTS + widget * TapElement.entries.size + element.ordinal
        }

    /** The widget an intent's tap was on; none for a plain text's tap. */
    fun widget(intent: Intent): Int? =
        if (intent.hasExtra(WIDGET)) intent.getIntExtra(WIDGET, AppWidgetManager.INVALID_APPWIDGET_ID) else null

    /**
     * The app's sign-in screen, for a widget without data or without a
     * usable sign-in (D50, D52), started as the app's icon starts it, so a
     * screen already open comes to the front instead of a second one.
     */
    fun signIn(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            SIGN_IN_REQUEST,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(context, SignInActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
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
     * an entry without a UID or with a blank one.
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
    ): Tap? {
        val id = Tap.Entry.uidOf(uid)
        return if (id == null || day == null) null else Tap.Entry(id, day)
    }

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

/**
 * The taps of the widget id as its targets say (D63), for its drawing: a
 * tap where its element has one of its own, none where it opens nothing or,
 * for an entry, leaves the tap to its day's area. A widget's views are
 * applied again onto the views the launcher shows, so a tap the drawing
 * leaves out has to be cleared, with the none these give.
 */
class WidgetTaps(
    val id: Int,
    val targets: TapTargets,
) {
    /** The tap on a fixed part (header, "+"), none when its element has none. */
    fun pendingIntent(
        context: Context,
        tap: Tap,
    ): PendingIntent? = if (targets.opens(tap.element)) Taps.pendingIntent(context, tap, id) else null

    /** The fill-in of a list row's cell, none when its tap's element has no tap. */
    fun fillIn(tap: Tap): Intent? = if (targets.opens(tap.element)) Taps.fillIn(tap, id) else null
}
