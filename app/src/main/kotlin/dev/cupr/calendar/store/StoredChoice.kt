package dev.cupr.calendar.store

import android.content.SharedPreferences
import android.util.Log

/** A choice kept in the preferences under [stored], a name fixed apart from the enum's names. */
interface StoredChoice {
    val stored: String
}

/**
 * The choice of entries stored under key, default when none is stored; one
 * stored under a name this app does not know reads as default, and the log
 * says so under tag, after what names it.
 */
internal fun <T : StoredChoice> SharedPreferences.choice(
    key: String,
    entries: List<T>,
    default: T,
    tag: String,
    what: String,
): T {
    val name = getString(key, null) ?: return default
    return entries.firstOrNull { it.stored == name } ?: default.also {
        Log.w(tag, "$what \"$name\" is unknown; it reads as ${it.stored}")
    }
}
