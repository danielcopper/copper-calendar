package dev.cupr.calendar.model

/**
 * The kinds the widgets' display rules look at, as Home Assistant names them.
 * An entry's kind stays an open string (D4); these are only the ones the app
 * treats specially.
 */
object Kinds {
    /** A special day (Martinstag, 1. Advent, …). */
    const val SPECIAL = "special"

    /** A public holiday. */
    const val HOLIDAY = "holiday"

    /** A birthday; with its year of birth the week says how old the person turns. */
    const val BIRTHDAY = "birthday"

    /** School holidays. */
    const val SCHOOL_HOLIDAYS = "holidays"

    /** A vacation. */
    const val VACATION = "vacation"
}
