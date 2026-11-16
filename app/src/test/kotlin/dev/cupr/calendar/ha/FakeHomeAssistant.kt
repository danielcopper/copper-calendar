package dev.cupr.calendar.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * A Home Assistant on a local MockWebServer, speaking the parts the app uses:
 * `/auth/token`, `/auth/revoke`, `/api/states/sensor.family_calendar` and
 * the WebSocket with `auth`, `auth/current_user` and `frontend/get_themes`,
 * in the forms Core 2026.11.1 answers with. Each part's answer can be set.
 */
class FakeHomeAssistant : Closeable {
    private val server = MockWebServer()

    /** The access token the WebSocket and the states accept. */
    var accessToken = "access-1"

    /**
     * The challenge the login page was given, as `/auth/authorize` would
     * keep it: when set, the code is traded only for a `code_verifier` whose
     * SHA-256, base64url without padding, is this challenge, as Core checks
     * it (`_verify_code_verifier`).
     */
    var challenge: String? = null

    /** When set, a request to `/auth/token` waits for it, after counting down [tokenAsked]. */
    var tokenGate: CountDownLatch? = null

    val tokenAsked = CountDownLatch(1)

    /** Counts down when the app closes a WebSocket. */
    val webSocketClosedByApp = CountDownLatch(1)

    /** The answer to `/auth/token`, by the request's form fields. */
    var token: (Map<String, String>) -> MockResponse = { form ->
        when {
            form["grant_type"] != "authorization_code" -> {
                json("""{"access_token":"$accessToken","token_type":"Bearer","expires_in":1800}""")
            }

            challenge != null && s256(form["code_verifier"].orEmpty()) != challenge -> {
                json("""{"error":"invalid_grant","error_description":"Invalid code verifier"}""", 400)
            }

            else -> {
                json(
                    """{"access_token":"$accessToken","token_type":"Bearer",""" +
                        """"refresh_token":"refresh-1","expires_in":1800}""",
                )
            }
        }
    }

    /** The answer to `/auth/revoke`; Core answers 200 for any token. */
    var revoke: () -> MockResponse = { MockResponse.Builder().code(200).build() }

    /** The sensor's state as JSON, or another answer to `/api/states/sensor.family_calendar`. */
    var states: () -> MockResponse = { json("""{"entity_id":"sensor.family_calendar","attributes":{"entries":[]}}""") }

    /** When set, a request for the states waits for it, after counting down [statesAsked]. */
    var statesGate: CountDownLatch? = null

    val statesAsked = CountDownLatch(1)

    var user = "Daniel"

    /** `frontend/get_themes`' `themes`. */
    var themes = """{"Zuhause":{"primary-color":"#1f6fd6"},"Other":{}}"""

    /** Home Assistant's error code for `frontend/get_themes`, which then fails; none: it succeeds. */
    var themesError: String? = null

    /** False: the WebSocket answers nothing after `auth_required`. */
    var webSocketAnswers = true

    /** When set, the WebSocket's upgrade is answered with this status instead. */
    var webSocketStatus: Int? = null

    /** True: the WebSocket closes right after `auth_required`, as Home Assistant does when it shuts down. */
    var webSocketClosesFirst = false

    /** The requests to the HTTP endpoints, in order, with their form fields where they have a form. */
    val requests = CopyOnWriteArrayList<Pair<RecordedRequest, Map<String, String>>>()

    /** The WebSocket's messages from the app, in order. */
    val webSocketMessages = CopyOnWriteArrayList<JsonObject>()

    val url: HttpUrl
        get() = server.url("/")

    /** The fake's address; plain http, which [HaAddress.parse] would refuse. */
    val address: HaAddress
        get() = HaAddress(url)

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val form = form(request)
                    requests += request to form
                    return when (request.url.encodedPath) {
                        "/auth/token" -> {
                            tokenAsked.countDown()
                            tokenGate?.await()
                            token(form)
                        }

                        "/auth/revoke" -> {
                            revoke()
                        }

                        "/api/states/sensor.family_calendar" -> {
                            states(request)
                        }

                        "/api/websocket" -> {
                            webSocketStatus?.let { json("""{"message":"…"}""", it) }
                                ?: MockResponse.Builder().webSocketUpgrade(WebSocketSide()).build()
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
                }
            }
        server.start()
    }

    private fun states(request: RecordedRequest): MockResponse {
        statesAsked.countDown()
        statesGate?.await()
        return if (request.headers["Authorization"] == "Bearer $accessToken") {
            states()
        } else {
            json("""{"message":"Unauthorized"}""", 401)
        }
    }

    /** Stops answering: every request after this fails to connect. */
    override fun close() = server.close()

    private inner class WebSocketSide : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            webSocket.send("""{"type":"auth_required","ha_version":"2026.11.1"}""")
            if (webSocketClosesFirst) webSocket.close(1001, "going away")
        }

        /** Answers the app's close, as Home Assistant's server does. */
        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            if (!webSocketClosesFirst) webSocketClosedByApp.countDown()
            webSocket.close(code, null)
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            val message = Json.parseToJsonElement(text).jsonObject
            webSocketMessages += message
            if (!webSocketAnswers) return
            val id = (message["id"] as? JsonPrimitive)?.content
            when ((message["type"] as JsonPrimitive).content) {
                "auth" -> {
                    if ((message["access_token"] as? JsonPrimitive)?.content == accessToken) {
                        webSocket.send("""{"type":"auth_ok","ha_version":"2026.11.1"}""")
                    } else {
                        webSocket.send("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
                    }
                }

                "auth/current_user" -> {
                    webSocket.send(
                        """{"id":$id,"type":"result","success":true,""" +
                            """"result":{"id":"u1","name":"$user","is_owner":true}}""",
                    )
                }

                "frontend/get_themes" -> {
                    webSocket.send(
                        themesError?.let { failed(id, it) }
                            ?: (
                                """{"id":$id,"type":"result","success":true,""" +
                                    """"result":{"themes":$themes,"default_theme":"default","default_dark_theme":null}}"""
                            ),
                    )
                }

                else -> {
                    webSocket.send(failed(id, "unknown_command"))
                }
            }
        }

        private fun failed(
            id: String?,
            code: String,
        ) = """{"id":$id,"type":"result","success":false,"error":{"code":"$code","message":"…"}}"""
    }

    companion object {
        fun json(
            body: String,
            code: Int = 200,
        ): MockResponse =
            MockResponse
                .Builder()
                .code(code)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build()

        /** RFC 7636's S256, written out here so the test does not check the app with the app's own code. */
        fun s256(verifier: String): String =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        private fun form(request: RecordedRequest): Map<String, String> {
            val body = request.body?.utf8().orEmpty()
            if (request.headers["Content-Type"]?.startsWith("application/x-www-form-urlencoded") !=
                true
            ) {
                return emptyMap()
            }
            return HttpUrl
                .Builder()
                .scheme("http")
                .host("form")
                .encodedQuery(body)
                .build()
                .let { url -> url.queryParameterNames.associateWith { url.queryParameter(it).orEmpty() } }
        }
    }
}
