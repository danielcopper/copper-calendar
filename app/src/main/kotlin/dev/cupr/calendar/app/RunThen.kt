package dev.cupr.calendar.app

/**
 * Runs [block], then [after] whatever its outcome, as `try`/`finally` does,
 * except that when both throw, [block]'s exception travels with [after]'s
 * suppressed in it instead of being replaced by it, as try-with-resources
 * keeps both.
 */
internal fun <T> runThen(
    block: () -> T,
    after: () -> Unit,
): T = AutoCloseable(after).use { block() }
