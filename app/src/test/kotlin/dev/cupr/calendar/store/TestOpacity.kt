package dev.cupr.calendar.store

/** The card's opacity of percent, a step of 10 from 0 to 100, as the tests write it. */
internal fun cardOpacity(percent: Int): CardOpacity = checkNotNull(CardOpacity.of(percent)) { "No opacity: $percent" }
