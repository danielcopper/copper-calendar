package dev.cupr.calendar.store

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant

/**
 * The theme "Zuhause" the last fetch that read Home Assistant's answer could
 * not read (D48): [keptFrom] is when the theme the snapshot keeps instead
 * was fetched, none when it keeps none and the built-in colours draw.
 */
data class UnreadableTheme(
    val keptFrom: Instant?,
)

/** Keeps the [UnreadableTheme] for the sign-in screen; how long: D48. */
class UnreadableThemeStore(
    private val prefs: SharedPreferences,
) {
    fun read(): UnreadableTheme? {
        if (!prefs.getBoolean(UNREADABLE, false)) return null
        val keptFrom = prefs.getLong(KEPT_FROM, -1)
        return UnreadableTheme(if (keptFrom < 0) null else Instant.ofEpochMilli(keptFrom))
    }

    fun write(theme: UnreadableTheme) =
        prefs.edit {
            putBoolean(UNREADABLE, true)
            val keptFrom = theme.keptFrom
            if (keptFrom == null) remove(KEPT_FROM) else putLong(KEPT_FROM, keptFrom.toEpochMilli())
        }

    fun clear() =
        prefs.edit {
            remove(UNREADABLE)
            remove(KEPT_FROM)
        }

    private companion object {
        const val UNREADABLE = "theme_unreadable"
        const val KEPT_FROM = "theme_kept_from"
    }
}
