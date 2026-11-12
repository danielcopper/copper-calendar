package dev.cupr.calendar.ha

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInRequestTest {
    private val address = HaAddress("https://home.example/".toHttpUrl())
    private val request = SignInRequest(address, "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "state-1")

    @Test
    fun `the challenge is S256 of the verifier, as RFC 7636's example`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SignInRequest.challenge(request.verifier))
    }

    @Test
    fun `a new sign-in has a verifier of RFC 7636's least length and its own state`() {
        val one = SignInRequest.start(address)
        val two = SignInRequest.start(address)

        assertEquals(43, one.verifier.length)
        assertTrue(one.verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertNotEquals(one.verifier, two.verifier)
        assertNotEquals(one.state, two.state)
    }

    @Test
    fun `the login page is asked for a code for the app's client and redirect`() {
        val url = request.authorizeUrl

        assertEquals(
            "https://home.example/auth/authorize",
            url
                .newBuilder()
                .query(null)
                .build()
                .toString(),
        )
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("http://127.0.0.1:47123/", url.queryParameter("client_id"))
        assertEquals("http://127.0.0.1:47123/callback", url.queryParameter("redirect_uri"))
        assertEquals("state-1", url.queryParameter("state"))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
    }

    @Test
    fun `the redirect with this sign-in's state brings its code`() {
        val callback = request.callback("http://127.0.0.1:47123/callback?code=abc&state=state-1&storeToken=true")

        assertEquals(AuthorizationCode("abc"), (callback as SignInRequest.Callback.Code).code)
    }

    @Test
    fun `the redirect with another state or without a code fails`() {
        assertEquals(
            SignInRequest.Callback.Failed("the redirect's state does not match"),
            request.callback("http://127.0.0.1:47123/callback?code=abc&state=other"),
        )
        assertEquals(
            SignInRequest.Callback.Failed("the redirect's state does not match"),
            request.callback("http://127.0.0.1:47123/callback?code=abc"),
        )
        assertEquals(
            SignInRequest.Callback.Failed("the redirect carries no code"),
            request.callback("http://127.0.0.1:47123/callback?state=state-1"),
        )
    }

    @Test
    fun `any other page is not the redirect`() {
        for (url in listOf(
            "https://home.example/auth/authorize?client_id=x",
            "http://127.0.0.1:47124/callback?code=abc&state=state-1",
            "http://127.0.0.1:47123/other?code=abc&state=state-1",
            "https://127.0.0.1:47123/callback?code=abc&state=state-1",
            "http://localhost:47123/callback?code=abc&state=state-1",
            "intent://whatever",
        )) {
            assertEquals(url, SignInRequest.Callback.NotOurs, request.callback(url))
        }
    }

    @Test
    fun `the client id and the redirect are on the same scheme, host and port, as Home Assistant requires`() {
        val client = SignInRequest.CLIENT_ID.toHttpUrl()
        val redirect = SignInRequest.REDIRECT_URI.toHttpUrl()

        assertEquals(client.scheme, redirect.scheme)
        assertEquals(client.host, redirect.host)
        assertEquals(client.port, redirect.port)
        assertEquals("127.0.0.1", client.host)
    }

    @Test
    fun `secrets never show in the text of a sign-in or its code`() {
        assertEquals("SignInRequest(…)", request.toString())
        assertEquals(
            "Code(code=AuthorizationCode(…))",
            SignInRequest.Callback.Code(AuthorizationCode("abc")).toString(),
        )
    }
}
