package dev.cupr.calendar.ha

import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Under Robolectric, since OkHttp's Android build reaches for android.util.Log. */
@RunWith(RobolectricTestRunner::class)
class TokenClientTest {
    private val home = FakeHomeAssistant()
    private val client = TokenClient(OkHttpClient(), home.address)
    private val request = SignInRequest(home.address, "verifier-1", "state-1")
    private val refresh = RefreshToken("refresh-1")
    private val code = AuthorizationCode("code-1")

    @After
    fun stop() = home.close()

    @Test
    fun `the code is traded for the refresh token with the client id and the verifier`() {
        val exchange = client.exchange(code, request)

        assertEquals(TokenClient.Exchange.Signed(RefreshToken("refresh-1")), exchange)
        val (sent, form) = home.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/auth/token", sent.url.encodedPath)
        assertEquals(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to "code-1",
                "client_id" to "http://127.0.0.1:47123/",
                "code_verifier" to "verifier-1",
            ),
            form,
        )
    }

    @Test
    fun `a refused code, or an answer without a refresh token, gives no sign-in and says why without the code`() {
        home.token =
            { FakeHomeAssistant.json("""{"error":"invalid_grant","error_description":"Invalid code verifier"}""", 400) }
        assertEquals(TokenClient.Exchange.Failed("HTTP 400 invalid_grant"), client.exchange(code, request))

        home.token = { FakeHomeAssistant.json("""{"access_token":"access-1","token_type":"Bearer"}""") }
        assertEquals(TokenClient.Exchange.Failed("HTTP 200"), client.exchange(code, request))
    }

    @Test
    fun `a refresh sends the refresh token with the client id it was issued to`() {
        val result = client.refresh(refresh, "http://127.0.0.1:47123/")

        assertEquals(TokenClient.Refresh.Access(AccessToken("access-1")), result)
        assertEquals(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to "refresh-1",
                "client_id" to "http://127.0.0.1:47123/",
            ),
            home.requests.single().second,
        )
    }

    @Test
    fun `Home Assistant refusing the refresh token is a rejection`() {
        for ((code, error) in listOf(400 to "invalid_grant", 400 to "invalid_request", 403 to "access_denied")) {
            home.token = { FakeHomeAssistant.json("""{"error":"$error"}""", code) }

            assertEquals(TokenClient.Refresh.Rejected("HTTP $code $error"), client.refresh(refresh, "client"))
        }
    }

    @Test
    fun `any other answer to a refresh is a failure, not a rejection`() {
        val unexpected = FailureKind.UNEXPECTED
        val answers =
            listOf(
                FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 500) to
                    failed("HTTP 500 invalid_grant", unexpected),
                FakeHomeAssistant.json("""{"error":"unsupported_grant_type"}""", 400) to
                    failed("HTTP 400 unsupported_grant_type", unexpected),
                FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 403) to
                    failed("HTTP 403 invalid_grant", unexpected),
                FakeHomeAssistant.json("""{"token_type":"Bearer"}""") to failed("HTTP 200", unexpected),
                // A gateway's statuses, Home Assistant offline behind Nabu Casa: no connection (owner, D46).
                FakeHomeAssistant.json("Bad Gateway", 502) to failed("HTTP 502", FailureKind.UNREACHABLE),
                FakeHomeAssistant.json("Service Unavailable", 503) to failed("HTTP 503", FailureKind.UNREACHABLE),
                FakeHomeAssistant.json("Gateway Timeout", 504) to failed("HTTP 504", FailureKind.UNREACHABLE),
            )
        for ((answer, expected) in answers) {
            home.token = { answer }

            assertEquals(expected, client.refresh(refresh, "client"))
        }
    }

    private fun failed(
        reason: String,
        kind: FailureKind,
    ) = TokenClient.Refresh.Failed(reason, kind)

    @Test
    fun `no answer at all is a failure named by the exception's class`() {
        home.close()

        assertEquals(
            TokenClient.Refresh.Failed("ConnectException", FailureKind.UNREACHABLE),
            client.refresh(refresh, "client"),
        )
        assertEquals(TokenClient.Exchange.Failed("ConnectException"), client.exchange(code, request))
        assertEquals(TokenClient.Revoke.Failed("ConnectException"), client.revoke(refresh))
    }

    @Test
    fun `revoking sends the refresh token to the revocation endpoint`() {
        assertEquals(TokenClient.Revoke.Ended, client.revoke(refresh))

        val (sent, form) = home.requests.single()
        assertEquals("/auth/revoke", sent.url.encodedPath)
        assertEquals(mapOf("token" to "refresh-1"), form)
    }

    @Test
    fun `a revocation Home Assistant answers with an error is a failure`() {
        home.revoke = { FakeHomeAssistant.json("Server error", 500) }

        assertEquals(TokenClient.Revoke.Failed("HTTP 500"), client.revoke(refresh))
    }

    @Test
    fun `tokens never show in their text`() {
        assertEquals("RefreshToken(…)", RefreshToken("refresh-1").toString())
        assertEquals("AuthorizationCode(…)", code.toString())
        assertEquals(
            "Access(accessToken=AccessToken(…))",
            TokenClient.Refresh.Access(AccessToken("access-1")).toString(),
        )
        assertEquals("Signed(refreshToken=RefreshToken(…))", TokenClient.Exchange.Signed(refresh).toString())
    }
}
