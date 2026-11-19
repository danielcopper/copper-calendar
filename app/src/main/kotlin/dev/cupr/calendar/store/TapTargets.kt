package dev.cupr.calendar.store

/**
 * What a tap on one of a widget's elements opens (D63), kept under [stored].
 * The header, a day and "+" take a [PartTarget], an entry an [EntryTarget].
 */
sealed interface TapTarget {
    val stored: String

    /** The element's place on the calendar page, every element's default. */
    data object CalendarPage : PartTarget, EntryTarget {
        override val stored = "calendar"
    }

    /** An entry's day on the calendar page, instead of the entry. */
    data object EntryDay : EntryTarget {
        override val stored = "day"
    }

    /** Another app, by its package, started as its icon starts it. */
    data class App(
        val packageName: String,
    ) : PartTarget {
        init {
            require(packageName.isNotBlank()) { "An app target needs its package" }
        }

        override val stored = "$APP_PREFIX$packageName"
    }

    /** No tap at all. */
    data object None : PartTarget, EntryTarget {
        override val stored = "nothing"
    }

    companion object {
        private const val APP_PREFIX = "app:"

        /** The target stored under name, none for a name this app does not know. */
        fun of(name: String): TapTarget? =
            when {
                name.startsWith(APP_PREFIX) -> name.removePrefix(APP_PREFIX).takeIf { it.isNotBlank() }?.let(::App)
                else -> listOf(CalendarPage, EntryDay, None).firstOrNull { it.stored == name }
            }
    }
}

/** A target of the header, a day or "+". */
sealed interface PartTarget : TapTarget

/** A target of an entry ("Woche" only, D37): no other app. */
sealed interface EntryTarget : TapTarget

/** A widget's tap targets, one per element; each defaults to the calendar page. */
data class TapTargets(
    val header: PartTarget = TapTarget.CalendarPage,
    val day: PartTarget = TapTarget.CalendarPage,
    val entry: EntryTarget = TapTarget.CalendarPage,
    val new: PartTarget = TapTarget.CalendarPage,
) {
    companion object {
        val DEFAULT = TapTargets()
    }
}
