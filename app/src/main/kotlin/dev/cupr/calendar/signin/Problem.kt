package dev.cupr.calendar.signin

import androidx.annotation.StringRes
import dev.cupr.calendar.R

/** Why the last sign-in did not come about, as the screen says it (D43). */
enum class Problem(
    @param:StringRes val text: Int,
) {
    /** The login page did not load. */
    UNREACHABLE(R.string.sign_in_unreachable),

    /** Anything else: a redirect this sign-in cannot use, no tokens, or a token that cannot be sealed. */
    FAILED(R.string.sign_in_failed),
}
