package dev.cupr.calendar.ha

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One sign-in with Home Assistant's own login (D9): OAuth's authorization
 * code with PKCE (S256) and a `state`, for the app's `client_id` and
 * redirect on 127.0.0.1 (D10). The login page runs in the sign-in screen's
 * WebView, which catches the redirect to [REDIRECT_URI] before it loads, so
 * nothing listens on that port (D43).
 *
 * A new sign-in comes from [start]. [verifier] and [state] are secrets of
 * this sign-in only; neither, nor the code, is ever logged.
 */
class SignInRequest internal constructor(
    val address: HaAddress,
    internal val verifier: String,
    internal val state: String,
) {
    /** The login page, `/auth/authorize` with the app's client, the redirect, the challenge and the state. */
    val authorizeUrl: HttpUrl
        get() =
            address
                .resolve("auth/authorize")
                .newBuilder()
                .addQueryParameter("response_type", "code")
                .addQueryParameter("client_id", CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", challenge(verifier))
                .addQueryParameter("code_challenge_method", "S256")
                .build()

    /** What a URL the login page navigates to means for this sign-in. */
    fun callback(url: String): Callback {
        val parsed = url.toHttpUrlOrNull()
        val code = parsed?.queryParameter("code")
        return when {
            parsed == null || withoutQuery(parsed) != REDIRECT -> Callback.NotOurs
            parsed.queryParameter("state") != state -> Callback.Failed("the redirect's state does not match")
            code.isNullOrEmpty() -> Callback.Failed("the redirect carries no code")
            else -> Callback.Code(AuthorizationCode(code))
        }
    }

    private fun withoutQuery(url: HttpUrl) =
        url
            .newBuilder()
            .query(null)
            .fragment(null)
            .build()

    sealed interface Callback {
        /** Not the redirect: the login page goes on loading it. */
        data object NotOurs : Callback

        /** The redirect with this sign-in's code. */
        data class Code(
            val code: AuthorizationCode,
        ) : Callback

        /** The redirect, but not one this sign-in can use; [reason] holds no secret. */
        data class Failed(
            val reason: String,
        ) : Callback
    }

    override fun toString() = "SignInRequest(…)"

    companion object {
        /**
         * The app's `client_id`, kept with the refresh token in exactly this
         * form, since a refresh needs the same string (D10). The port is any
         * fixed one.
         */
        const val CLIENT_ID = "http://127.0.0.1:47123/"

        /** On the client's scheme, host and port, which Home Assistant accepts without fetching anything (D10). */
        const val REDIRECT_URI = CLIENT_ID + "callback"

        private val REDIRECT = REDIRECT_URI.toHttpUrl()

        /** 32 random bytes make a 43-character verifier, RFC 7636's least length. */
        private const val VERIFIER_BYTES = 32
        private const val STATE_BYTES = 16

        /** A new sign-in at [address], with a fresh verifier and state from [random]. */
        fun start(
            address: HaAddress,
            random: SecureRandom = SecureRandom(),
        ) = SignInRequest(address, randomString(random, VERIFIER_BYTES), randomString(random, STATE_BYTES))

        /** RFC 7636's S256: the verifier's SHA-256, base64url without padding. */
        fun challenge(verifier: String): String =
            base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        /** [bytes] random bytes, base64url without padding. */
        private fun randomString(
            random: SecureRandom,
            bytes: Int,
        ): String = base64Url(ByteArray(bytes).also(random::nextBytes))

        private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
