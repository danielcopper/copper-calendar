package dev.cupr.calendar.ha

/** Home Assistant's refresh token; its text never shows, so it cannot end up in a log by accident. */
@JvmInline
value class RefreshToken(
    val value: String,
) {
    override fun toString() = "RefreshToken(…)"
}

/** An access token, kept in memory for one fetch (D45); its text never shows. */
@JvmInline
value class AccessToken(
    val value: String,
) {
    override fun toString() = "AccessToken(…)"
}

/** The authorization code a sign-in's redirect carries, traded once for the tokens; its text never shows. */
@JvmInline
value class AuthorizationCode(
    val value: String,
) {
    override fun toString() = "AuthorizationCode(…)"
}
