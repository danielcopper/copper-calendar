package dev.cupr.calendar.ha

import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Home Assistant's token endpoints (`components/auth/__init__.py` at Core
 * 2026.11.1): `/auth/token` for the authorization code and for a refresh,
 * `/auth/revoke` to end a refresh token. Requests are form-encoded, as
 * Home Assistant reads them.
 *
 * Nothing secret leaves this class in a result's text: a failure names the
 * HTTP status, Home Assistant's error code or the exception's class, never
 * a token, the code or the address.
 */
class TokenClient(
    private val http: OkHttpClient,
    private val address: HaAddress,
) {
    sealed interface Exchange {
        /** The refresh token of the new sign-in; the access token that comes with it is not used (D45). */
        data class Signed(
            val refreshToken: RefreshToken,
        ) : Exchange

        data class Failed(
            val reason: String,
        ) : Exchange
    }

    sealed interface Refresh {
        data class Access(
            val accessToken: AccessToken,
        ) : Refresh

        /**
         * Home Assistant refuses the refresh token: 400 with `invalid_grant`
         * (it does not know the token) or `invalid_request` (another
         * client_id), or 403 with `access_denied` (the user may no longer
         * sign in). Signing in again is the only way on (D46).
         */
        data class Rejected(
            val reason: String,
        ) : Refresh

        /** No answer, or another one; the refresh token may still be good. */
        data class Failed(
            val reason: String,
            val kind: FailureKind,
        ) : Refresh
    }

    sealed interface Revoke {
        /** Home Assistant answered 200, which it does for a token it ended and for one it does not know. */
        data object Ended : Revoke

        data class Failed(
            val reason: String,
        ) : Revoke
    }

    /** Trades the code [request]'s redirect carried for tokens, with its verifier. */
    fun exchange(
        code: AuthorizationCode,
        request: SignInRequest,
    ): Exchange =
        try {
            post(
                "auth/token",
                "grant_type" to "authorization_code",
                "code" to code.value,
                "client_id" to SignInRequest.CLIENT_ID,
                "code_verifier" to request.verifier,
            ).use { response ->
                val body = body(response)
                val refresh = body?.string("refresh_token")
                if (response.isSuccessful && refresh != null) {
                    Exchange.Signed(RefreshToken(refresh))
                } else {
                    Exchange.Failed(describe(response, body))
                }
            }
        } catch (e: IOException) {
            Exchange.Failed(e.javaClass.simpleName)
        }

    /** A new access token for [refreshToken], issued to [clientId]. */
    fun refresh(
        refreshToken: RefreshToken,
        clientId: String,
    ): Refresh =
        try {
            post(
                "auth/token",
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken.value,
                "client_id" to clientId,
            ).use { response ->
                val body = body(response)
                val access = body?.string("access_token")
                val error = body?.string("error")
                when {
                    response.isSuccessful && access != null -> Refresh.Access(AccessToken(access))
                    response.code == BAD_REQUEST && error in REJECTED_400 -> Refresh.Rejected(describe(response, body))
                    response.code == FORBIDDEN && error == ACCESS_DENIED -> Refresh.Rejected(describe(response, body))
                    else -> Refresh.Failed(describe(response, body), FailureKind.ofStatus(response.code))
                }
            }
        } catch (e: IOException) {
            Refresh.Failed(e.javaClass.simpleName, FailureKind.of(e))
        }

    /** Asks Home Assistant to end [refreshToken] and every access token issued with it. */
    fun revoke(refreshToken: RefreshToken): Revoke =
        try {
            post("auth/revoke", "token" to refreshToken.value).use { response ->
                if (response.isSuccessful) Revoke.Ended else Revoke.Failed("HTTP ${response.code}")
            }
        } catch (e: IOException) {
            Revoke.Failed(e.javaClass.simpleName)
        }

    private fun post(
        path: String,
        vararg fields: Pair<String, String>,
    ): Response {
        val form = FormBody.Builder()
        fields.forEach { (name, value) -> form.add(name, value) }
        val request =
            Request
                .Builder()
                .url(address.resolve(path))
                .post(form.build())
                .build()
        return http.newCall(request).execute()
    }

    private fun body(response: Response): JsonObject? = parseObject(response.body.string())

    private fun describe(
        response: Response,
        body: JsonObject?,
    ): String = listOfNotNull("HTTP ${response.code}", body?.string("error")).joinToString(" ")

    private companion object {
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val ACCESS_DENIED = "access_denied"
        val REJECTED_400 = setOf("invalid_grant", "invalid_request")
    }
}
