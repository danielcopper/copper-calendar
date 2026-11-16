package dev.cupr.calendar.ha

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The address of the Home Assistant instance, the root of an https URL
 * (D42): what [parse] makes of the text typed on the sign-in screen, or
 * what [stored] makes of the form the account and a pending sign-in keep.
 * The rest of the app takes the address only in this form, so every path is
 * resolved below the instance's root.
 *
 * The constructor is internal, not private, for the tests' fake Home
 * Assistant on plain http, which [parse] refuses.
 */
@JvmInline
value class HaAddress internal constructor(
    val url: HttpUrl,
) {
    /** [path] below the root, such as `auth/token`. */
    fun resolve(path: String): HttpUrl = url.newBuilder().addPathSegments(path).build()

    companion object {
        /**
         * The address the account or a pending sign-in stored, which [parse]
         * made, cut to its root again; plain http, which only the tests' fake
         * stores, is accepted too.
         */
        internal fun stored(text: String): HaAddress? = text.toHttpUrlOrNull()?.let { HaAddress(root(it)) }

        /**
         * The address typed on the sign-in screen: only https, since the app
         * reaches Home Assistant through the Nabu Casa URL (D9) and Android
         * refuses cleartext; without a scheme https is assumed. A path, query
         * or fragment typed after the host is dropped.
         */
        fun parse(input: String): HaAddress? {
            val text = input.trim()
            val withScheme = if ("://" in text) text else "https://$text"
            return withScheme
                .takeIf { text.isNotEmpty() }
                ?.toHttpUrlOrNull()
                ?.takeIf { it.isHttps }
                ?.let { HaAddress(root(it)) }
        }

        /** [url]'s scheme, host and port alone. */
        private fun root(url: HttpUrl): HttpUrl =
            HttpUrl
                .Builder()
                .scheme(url.scheme)
                .host(url.host)
                .port(url.port)
                .build()
    }
}
