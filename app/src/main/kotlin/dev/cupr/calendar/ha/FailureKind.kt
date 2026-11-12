package dev.cupr.calendar.ha

import java.io.IOException
import java.io.InterruptedIOException

/**
 * What kind of failure an answer from Home Assistant, or its absence, was;
 * the sign-in screen names it in plain words (D46), the log keeps the
 * technical reason.
 */
enum class FailureKind {
    /**
     * No connection, or a broken one: the host is unknown, refuses, or its
     * TLS fails; a gateway answers 502, 503 or 504; or Home Assistant closed
     * the WebSocket.
     */
    UNREACHABLE,

    /** No answer in time. */
    TIMEOUT,

    /** Home Assistant has no `sensor.family_calendar`, or it has no list of entries. */
    NOT_FOUND,

    /** Home Assistant refused the access token. */
    REFUSED,

    /** Any other answer: another status, or one that is not what Home Assistant sends. */
    UNEXPECTED,
    ;

    companion object {
        /** A gateway's statuses: Home Assistant behind it, such as behind Nabu Casa, is out of reach. */
        private val GATEWAY = setOf(502, 503, 504)

        /**
         * An HTTP status no other rule names: a gateway's 502, 503 or 504 is
         * no connection (owner, D46), any other unexpected.
         */
        fun ofStatus(status: Int): FailureKind = if (status in GATEWAY) UNREACHABLE else UNEXPECTED

        /** An OkHttp call's exception: a timeout (OkHttp's call timeout, a socket's read) or no connection. */
        fun of(e: Throwable): FailureKind =
            when (e) {
                is InterruptedIOException -> TIMEOUT
                is IOException -> UNREACHABLE
                else -> UNEXPECTED
            }
    }
}
