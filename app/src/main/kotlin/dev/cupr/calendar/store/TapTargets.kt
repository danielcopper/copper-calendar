package dev.cupr.calendar.store

/** What a tap on a widget hit (design.md, "Taps"). */
enum class TapElement {
    HEADER,
    DAY,
    ENTRY,
    NEW,
    ;

    /**
     * The kinds of target the element can have, in the setup's order (D63):
     * the header, a day and "+" their place on the calendar page, another
     * app or nothing; an entry its place, its day, or what a tap on its day
     * opens.
     */
    val kinds: List<TapTarget.Kind>
        get() =
            if (this == ENTRY) {
                listOf(TapTarget.Kind.CALENDAR_PAGE, TapTarget.Kind.ENTRY_DAY, TapTarget.Kind.AS_DAY)
            } else {
                listOf(TapTarget.Kind.CALENDAR_PAGE, TapTarget.Kind.APP, TapTarget.Kind.NONE)
            }

    /** Whether the element can have target, one of its [kinds]. */
    fun can(target: TapTarget): Boolean = target.kind in kinds

    /** The target stored under name, none for a name this app does not know or a target the element cannot have. */
    fun target(name: String): TapTarget? = TapTarget.of(name)?.takeIf(::can)
}

/** What a tap on one of a widget's elements opens (D63), kept under [stored]; [TapElement.kinds] says which. */
sealed interface TapTarget : StoredChoice {
    val kind: Kind

    /** The kinds of target, one each but [APP], one per app. */
    enum class Kind { CALENDAR_PAGE, ENTRY_DAY, APP, NONE, AS_DAY }

    /** The element's place on the calendar page, every element's default. */
    data object CalendarPage : TapTarget {
        override val stored = "calendar"
        override val kind = Kind.CALENDAR_PAGE
    }

    /** An entry's day on the calendar page, instead of the entry. */
    data object EntryDay : TapTarget {
        override val stored = "day"
        override val kind = Kind.ENTRY_DAY
    }

    /** Another app, by its package, started as its icon starts it. */
    data class App(
        val packageName: String,
    ) : TapTarget {
        init {
            require(packageName.isNotBlank()) { "An app target needs its package" }
        }

        override val stored = "$APP_PREFIX$packageName"
        override val kind = Kind.APP
    }

    /** No tap at all. */
    data object None : TapTarget {
        override val stored = "nothing"
        override val kind = Kind.NONE
    }

    /** An entry without a tap of its own: its day's area, under it, takes the tap. */
    data object AsDay : TapTarget {
        override val stored = "as_day"
        override val kind = Kind.AS_DAY
    }

    companion object {
        private const val APP_PREFIX = "app:"
        private val fixed = listOf(CalendarPage, EntryDay, None, AsDay)

        /** The target stored under name, none for a name this app does not know. */
        fun of(name: String): TapTarget? =
            when {
                name.startsWith(APP_PREFIX) -> name.removePrefix(APP_PREFIX).takeIf { it.isNotBlank() }?.let(::App)
                else -> fixed.firstOrNull { it.stored == name }
            }

        /** The one target of kind, any kind but [Kind.APP], which takes its app. */
        fun of(kind: Kind): TapTarget {
            require(kind != Kind.APP) { "Another app's target takes its app" }
            return fixed.single { it.kind == kind }
        }
    }
}

/** A widget's tap targets, one per element, each of a kind its element can have; each defaults to the calendar page. */
data class TapTargets(
    val header: TapTarget = TapTarget.CalendarPage,
    val day: TapTarget = TapTarget.CalendarPage,
    val entry: TapTarget = TapTarget.CalendarPage,
    val new: TapTarget = TapTarget.CalendarPage,
) {
    init {
        for (element in TapElement.entries) {
            val target = of(element)
            require(element.can(target)) { "$element cannot have $target" }
        }
    }

    /** The target of element. */
    fun of(element: TapElement): TapTarget =
        when (element) {
            TapElement.HEADER -> header
            TapElement.DAY -> day
            TapElement.ENTRY -> entry
            TapElement.NEW -> new
        }

    /** These targets with element's set to target, which has to be of a kind element can have. */
    fun with(
        element: TapElement,
        target: TapTarget,
    ): TapTargets =
        when (element) {
            TapElement.HEADER -> copy(header = target)
            TapElement.DAY -> copy(day = target)
            TapElement.ENTRY -> copy(entry = target)
            TapElement.NEW -> copy(new = target)
        }

    /** Whether element has a tap of its own: none when it opens nothing, or an entry when its day's area takes it. */
    fun opens(element: TapElement): Boolean = of(element).kind !in NO_TAP

    companion object {
        val DEFAULT = TapTargets()

        private val NO_TAP = setOf(TapTarget.Kind.NONE, TapTarget.Kind.AS_DAY)
    }
}
