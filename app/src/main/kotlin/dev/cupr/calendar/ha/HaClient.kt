package dev.cupr.calendar.ha

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * What the app reads from Home Assistant with an access token: the entries
 * of `sensor.family_calendar` over REST (D16), and over the WebSocket the
 * signed-in user's name (`auth/current_user`) and the theme "Zuhause"
 * (`frontend/get_themes`, D17).
 *
 * A failure has its [FailureKind] and a reason that names the HTTP status,
 * Home Assistant's message type or error code, or the exception's class,
 * never a token or the address.
 */
class HaClient(
    private val http: OkHttpClient,
    private val address: HaAddress,
    private val webSocketTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    sealed interface Result<out T> {
        data class Ok<T>(
            val value: T,
        ) : Result<T>

        data class Failed(
            val reason: String,
            val kind: FailureKind,
        ) : Result<Nothing>
    }

    /** The user's name and the theme "Zuhause", or none when Home Assistant has no theme of that name. */
    data class UserAndTheme(
        val user: String,
        val theme: JsonObject?,
    )

    /** The sensor's attribute `entries`, the list as Home Assistant sends it. */
    fun entries(accessToken: AccessToken): Result<JsonArray> {
        val request =
            Request
                .Builder()
                .url(address.resolve("api/states/$SENSOR"))
                .header("Authorization", "Bearer ${accessToken.value}")
                .build()
        return try {
            http.newCall(request).execute().use { response -> entriesOf(response) }
        } catch (e: IOException) {
            Result.Failed(e.javaClass.simpleName, FailureKind.of(e))
        }
    }

    private fun entriesOf(response: Response): Result<JsonArray> {
        val state = if (response.isSuccessful) parseObject(response.body.string()) else null
        val entries = (state?.get("attributes") as? JsonObject)?.get("entries") as? JsonArray
        return when {
            !response.isSuccessful -> {
                Result.Failed("HTTP ${response.code} for $SENSOR", kindOf(response.code))
            }

            state == null -> {
                Result.Failed(
                    "HTTP ${response.code} for $SENSOR, but no JSON object",
                    FailureKind.UNEXPECTED,
                )
            }

            entries == null -> {
                Result.Failed("$SENSOR has no list of entries", FailureKind.NOT_FOUND)
            }

            else -> {
                Result.Ok(entries)
            }
        }
    }

    /**
     * Opens the WebSocket, signs in with [accessToken], asks for the user and
     * the themes and closes it again; waits at most the client's timeout.
     */
    fun userAndTheme(accessToken: AccessToken): Result<UserAndTheme> {
        val events = LinkedBlockingQueue<Event>()
        val request = Request.Builder().url(address.resolve("api/websocket")).build()
        val socket = http.newWebSocket(request, Listener(events))
        return try {
            Conversation(socket, events, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(webSocketTimeoutMillis))
                .run(accessToken)
        } finally {
            if (!socket.close(NORMAL_CLOSURE, null)) socket.cancel()
        }
    }

    /** One run of the WebSocket's messages, in the order Home Assistant's `websocket_api` sends them. */
    private class Conversation(
        private val socket: WebSocket,
        private val events: LinkedBlockingQueue<Event>,
        private val deadline: Long,
    ) {
        fun run(accessToken: AccessToken): Result<UserAndTheme> {
            val results = mutableMapOf<Int, JsonElement>()
            var failure: Result.Failed? = null
            while (failure == null && results.size < 2) {
                failure =
                    when (val event = next()) {
                        null -> failed("no answer in time", FailureKind.TIMEOUT)
                        is Event.Ended -> failed(event.reason, event.kind)
                        is Event.Text -> handle(event.json, accessToken, results)
                    }
            }
            return failure ?: answer(results)
        }

        /** Answers one message; a failure, or none to go on. */
        private fun handle(
            message: JsonObject,
            accessToken: AccessToken,
            results: MutableMap<Int, JsonElement>,
        ): Result.Failed? =
            when (val type = message.string("type")) {
                "auth_required" -> {
                    socket.send(outgoing("auth", accessToken = accessToken.value))
                    null
                }

                "auth_ok" -> {
                    socket.send(outgoing("auth/current_user", id = USER))
                    socket.send(outgoing("frontend/get_themes", id = THEMES))
                    null
                }

                "result" -> {
                    collect(message, results)
                }

                "auth_invalid" -> {
                    failed(type, FailureKind.REFUSED)
                }

                else -> {
                    failed(type.toString(), FailureKind.UNEXPECTED)
                }
            }

        /** Keeps a successful result; a failed one's reason names Home Assistant's error code. */
        private fun collect(
            message: JsonObject,
            results: MutableMap<Int, JsonElement>,
        ): Result.Failed? {
            val id = (message["id"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it == USER || it == THEMES }
            val result = message["result"]?.takeIf { (message["success"] as? JsonPrimitive)?.content == "true" }
            if (id == null || result == null) {
                val code = (message["error"] as? JsonObject)?.string("code")
                return failed(
                    listOfNotNull("result ${message["id"]} failed", code).joinToString(": "),
                    FailureKind.UNEXPECTED,
                )
            }
            results[id] = result
            return null
        }

        private fun answer(results: Map<Int, JsonElement>): Result<UserAndTheme> {
            val user = (results[USER] as? JsonObject)?.string("name")
            val themes = (results[THEMES] as? JsonObject)?.get("themes") as? JsonObject
            return if (user == null || themes == null) {
                failed("an answer without user or themes", FailureKind.UNEXPECTED)
            } else {
                Result.Ok(UserAndTheme(user, themes[THEME] as? JsonObject))
            }
        }

        private fun failed(
            reason: String,
            kind: FailureKind,
        ) = Result.Failed("WebSocket: $reason", kind)

        private fun next(): Event? {
            val left = deadline - System.nanoTime()
            return if (left <= 0) null else events.poll(left, TimeUnit.NANOSECONDS)
        }

        private fun outgoing(
            type: String,
            id: Int? = null,
            accessToken: String? = null,
        ): String =
            buildJsonObject {
                id?.let { put("id", it) }
                put("type", type)
                accessToken?.let { put("access_token", it) }
            }.toString()
    }

    private sealed interface Event {
        class Text(
            val json: JsonObject,
        ) : Event

        class Ended(
            val reason: String,
            val kind: FailureKind,
        ) : Event
    }

    private class Listener(
        private val events: LinkedBlockingQueue<Event>,
    ) : WebSocketListener() {
        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            events.put(
                parseObject(text)?.let { Event.Text(it) }
                    ?: Event.Ended("a message that is not a JSON object", FailureKind.UNEXPECTED),
            )
        }

        /** Home Assistant, or a proxy before it, closes first; OkHttp calls [onClosed] only once the app closes too. */
        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            events.put(Event.Ended("closed by Home Assistant with $code", FailureKind.UNREACHABLE))
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            events.put(Event.Ended("closed with $code", FailureKind.UNREACHABLE))
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            // The upgrade carries no token, so its status says nothing about the token or the sensor: a gateway's
            // 502, 503 or 504 is no connection, any other status unexpected.
            val status = response?.let { " (HTTP ${it.code})" }.orEmpty()
            val kind = response?.let { FailureKind.ofStatus(it.code) } ?: FailureKind.of(t)
            events.put(Event.Ended(t.javaClass.simpleName + status, kind))
        }
    }

    companion object {
        const val SENSOR = "sensor.family_calendar"

        /** The theme the app reads (D17). */
        const val THEME = "Zuhause"

        private const val USER = 1
        private const val THEMES = 2
        private const val NORMAL_CLOSURE = 1000
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val NOT_FOUND = 404

        /** What an HTTP status other than success says about the states call, which carries the access token. */
        private fun kindOf(status: Int) =
            when (status) {
                UNAUTHORIZED, FORBIDDEN -> FailureKind.REFUSED
                NOT_FOUND -> FailureKind.NOT_FOUND
                else -> FailureKind.ofStatus(status)
            }

        /** How long the WebSocket's run may take, from opening it to the last answer. */
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}
