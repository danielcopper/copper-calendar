package dev.cupr.calendar.ha

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/** Under Robolectric, since OkHttp's Android build reaches for android.util.Log. */
@RunWith(RobolectricTestRunner::class)
class HaClientTest {
    private val home = FakeHomeAssistant()
    private val client = HaClient(OkHttpClient(), home.address, webSocketTimeoutMillis = 2_000)
    private val access = AccessToken("access-1")

    @After
    fun stop() = home.close()

    private fun failed(
        reason: String,
        kind: FailureKind,
    ) = HaClient.Result.Failed(reason, kind)

    @Test
    fun `the entries are the sensor's attribute, read with the access token`() {
        home.states = {
            FakeHomeAssistant.json(
                """{"entity_id":"sensor.family_calendar","state":"2026-11-12T10:00:00+01:00",""" +
                    """"attributes":{"entries":[{"title":"A"},{"title":"B"}]}}""",
            )
        }

        val entries = (client.entries(access) as HaClient.Result.Ok).value

        assertEquals(listOf("A", "B"), entries.map { (it.jsonObject["title"] as JsonPrimitive).content })
        val request = home.requests.single().first
        assertEquals("GET", request.method)
        assertEquals("/api/states/sensor.family_calendar", request.url.encodedPath)
        assertEquals("Bearer access-1", request.headers["Authorization"])
    }

    @Test
    fun `a refused token, a missing sensor, another status, no JSON or no entries is a failure of its kind`() {
        val sensor = "sensor.family_calendar"
        assertEquals(failed("HTTP 401 for $sensor", FailureKind.REFUSED), client.entries(AccessToken("another")))
        home.states = { FakeHomeAssistant.json("""{"message":"Forbidden"}""", 403) }
        assertEquals(failed("HTTP 403 for $sensor", FailureKind.REFUSED), client.entries(access))
        home.states = { FakeHomeAssistant.json("""{"message":"Entity not found."}""", 404) }
        assertEquals(failed("HTTP 404 for $sensor", FailureKind.NOT_FOUND), client.entries(access))
        home.states = { FakeHomeAssistant.json("Internal Server Error", 500) }
        assertEquals(failed("HTTP 500 for $sensor", FailureKind.UNEXPECTED), client.entries(access))
        // A gateway's statuses, Home Assistant offline behind Nabu Casa: no connection (owner, D46).
        for (gateway in listOf(502, 503, 504)) {
            home.states = { FakeHomeAssistant.json("Bad Gateway", gateway) }
            assertEquals(failed("HTTP $gateway for $sensor", FailureKind.UNREACHABLE), client.entries(access))
        }
        home.states = { FakeHomeAssistant.json("not json") }
        assertEquals(failed("HTTP 200 for $sensor, but no JSON object", FailureKind.UNEXPECTED), client.entries(access))
        home.states = { FakeHomeAssistant.json("""{"entity_id":"$sensor","attributes":{}}""") }
        assertEquals(failed("$sensor has no list of entries", FailureKind.NOT_FOUND), client.entries(access))
    }

    @Test
    fun `no answer for the entries is a failure named by the exception's class`() {
        home.close()

        assertEquals(failed("ConnectException", FailureKind.UNREACHABLE), client.entries(access))
    }

    @Test
    fun `the WebSocket signs in and brings the user's name and the theme Zuhause`() {
        val answer = (client.userAndTheme(access) as HaClient.Result.Ok).value

        assertEquals("Daniel", answer.user)
        assertEquals("#1f6fd6", (answer.theme!!["primary-color"] as JsonPrimitive).content)
        val auth = home.webSocketMessages.single { (it["type"] as JsonPrimitive).content == "auth" }
        assertEquals("access-1", (auth["access_token"] as JsonPrimitive).content)
    }

    @Test
    fun `the WebSocket is closed once its run is over`() {
        client.userAndTheme(access)

        assertTrue(home.webSocketClosedByApp.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `Home Assistant without the theme Zuhause gives no theme`() {
        home.themes = """{"Other":{}}"""

        val answer = (client.userAndTheme(access) as HaClient.Result.Ok).value

        assertNull(answer.theme)
    }

    @Test
    fun `a refused token on the WebSocket is a refusal`() {
        assertEquals(
            failed("WebSocket: auth_invalid", FailureKind.REFUSED),
            client.userAndTheme(AccessToken("another")),
        )
    }

    @Test
    fun `a failed command names Home Assistant's error code`() {
        home.themesError = "home_assistant_error"

        assertEquals(
            failed("WebSocket: result 2 failed: home_assistant_error", FailureKind.UNEXPECTED),
            client.userAndTheme(access),
        )
    }

    @Test(timeout = 10_000)
    fun `a WebSocket that answers nothing fails once the time is up`() {
        home.webSocketAnswers = false

        assertEquals(failed("WebSocket: no answer in time", FailureKind.TIMEOUT), client.userAndTheme(access))
    }

    @Test(timeout = 10_000)
    fun `a WebSocket that Home Assistant closes first fails at once, not when the time is up`() {
        home.webSocketClosesFirst = true
        val patient = HaClient(OkHttpClient(), home.address, webSocketTimeoutMillis = 60_000)

        assertEquals(
            failed("WebSocket: closed by Home Assistant with 1001", FailureKind.UNREACHABLE),
            patient.userAndTheme(access),
        )
    }

    @Test
    fun `a refused WebSocket upgrade, which carries no token, is no connection behind a gateway and unexpected else`() {
        for ((status, kind) in listOf(
            404 to FailureKind.UNEXPECTED,
            403 to FailureKind.UNEXPECTED,
            401 to FailureKind.UNEXPECTED,
            500 to FailureKind.UNEXPECTED,
            503 to FailureKind.UNREACHABLE,
        )) {
            home.webSocketStatus = status

            val result = client.userAndTheme(access) as HaClient.Result.Failed

            assertEquals(kind, result.kind)
            assertTrue(result.reason, result.reason.endsWith("(HTTP $status)"))
        }
    }

    @Test
    fun `no WebSocket at all is a failure named by the exception's class`() {
        home.close()

        assertEquals(failed("WebSocket: ConnectException", FailureKind.UNREACHABLE), client.userAndTheme(access))
    }

    @Test
    fun `the WebSocket's run may take 20 seconds by default`() {
        assertEquals(20_000L, HaClient.DEFAULT_TIMEOUT_MILLIS)
    }

    @Test
    fun `a timeout is told apart from no connection`() {
        assertEquals(FailureKind.TIMEOUT, FailureKind.of(java.net.SocketTimeoutException("read")))
        assertEquals(FailureKind.TIMEOUT, FailureKind.of(java.io.InterruptedIOException("timeout")))
        assertEquals(FailureKind.UNREACHABLE, FailureKind.of(java.net.UnknownHostException()))
        assertEquals(FailureKind.UNREACHABLE, FailureKind.of(javax.net.ssl.SSLHandshakeException("cert")))
        assertEquals(FailureKind.UNEXPECTED, FailureKind.of(IllegalStateException()))
    }
}
