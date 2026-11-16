package dev.cupr.calendar.signin

import android.app.Application
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.ha.SignInRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSslErrorHandler
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@RunWith(RobolectricTestRunner::class)
class LoginClientTest {
    private val view = WebView(ApplicationProvider.getApplicationContext<Application>())
    private val signIn = SignInRequest(HaAddress("https://home.example/".toHttpUrl()), "verifier-1", "state-1")
    private val callbacks = mutableListOf<SignInRequest.Callback>()
    private var unreachable = 0
    private val client = LoginClient(signIn, { callbacks += it }, { unreachable++ })

    private fun request(
        url: String,
        mainFrame: Boolean = true,
    ) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)

        override fun isForMainFrame() = mainFrame

        override fun isRedirect() = false

        override fun hasGesture() = false

        override fun getMethod() = "GET"

        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    /** A self-signed certificate for "untrusted.test", as a captive portal or a TLS proxy might show one. */
    private fun untrusted() =
        CertificateFactory
            .getInstance("X.509")
            .generateCertificate(UNTRUSTED.byteInputStream()) as X509Certificate

    private val logs: String
        get() = ShadowLog.getLogsForTag(SIGN_IN_TAG).joinToString("\n") { it.msg }

    @Test
    fun `any other page loads as usual`() {
        assertFalse(client.shouldOverrideUrlLoading(view, request("https://home.example/auth/authorize?x=1")))

        assertEquals(emptyList<SignInRequest.Callback>(), callbacks)
    }

    @Test
    fun `the redirect is caught before it loads and brings its code`() {
        val caught =
            client.shouldOverrideUrlLoading(
                view,
                request("http://127.0.0.1:47123/callback?code=abc&state=state-1"),
            )

        assertTrue(caught)
        assertEquals(AuthorizationCode("abc"), (callbacks.single() as SignInRequest.Callback.Code).code)
    }

    @Test
    fun `a redirect this sign-in cannot use is caught too and ends it`() {
        assertTrue(
            client.shouldOverrideUrlLoading(view, request("http://127.0.0.1:47123/callback?code=abc&state=other")),
        )

        assertEquals(listOf(SignInRequest.Callback.Failed("the redirect's state does not match")), callbacks)
    }

    @Test
    fun `an HTTP error for the page ends the sign-in, one for a part of it does not`() {
        val notFound = WebResourceResponse("text/html", "utf-8", 404, "Not Found", emptyMap(), null)

        client.onReceivedHttpError(view, request("https://home.example/missing.js", mainFrame = false), notFound)
        assertEquals(0, unreachable)

        client.onReceivedHttpError(view, request("https://home.example/auth/authorize"), notFound)
        assertEquals(1, unreachable)
        assertTrue(logs, logs.contains("The login page did not load: HTTP 404"))
    }

    @Test
    fun `a certificate the WebView does not trust ends the sign-in, logged by its error alone`() {
        val error = SslError(SslError.SSL_UNTRUSTED, SslCertificate(untrusted()), "https://home.example/auth/authorize")
        // SslErrorHandler's constructor is hidden; Robolectric's shadow records cancel() and proceed().
        val handler =
            SslErrorHandler::class.java
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()

        client.onReceivedSslError(view, handler, error)

        val shadow = Shadow.extract<ShadowSslErrorHandler>(handler)
        assertTrue(shadow.wasCancelCalled())
        assertFalse(shadow.wasProceedCalled())
        assertEquals(1, unreachable)
        assertTrue(logs, logs.contains("The login page did not load: TLS error ${SslError.SSL_UNTRUSTED}"))
        assertFalse(logs, logs.contains("home.example"))
    }

    private companion object {
        const val UNTRUSTED = """-----BEGIN CERTIFICATE-----
MIIBTDCB8qADAgECAgkA7/jvIPVZ5OYwCgYIKoZIzj0EAwMwGTEXMBUGA1UEAxMO
dW50cnVzdGVkLnRlc3QwIBcNMjYxMTEyMjA0OTAzWhgPMjEyNjEwMTkyMDQ5MDNa
MBkxFzAVBgNVBAMTDnVudHJ1c3RlZC50ZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAEvxM4QPTgmKNxyt9pWCjtS1C1ONMXaf8B+BGFedxe7mX+Cu87Dk7MHPZy
dcDPE/WrtbdHzG7WOaQphC1iQSk8v6MhMB8wHQYDVR0OBBYEFI7Jyr+XHnyZV0Fz
+HF37WZdujWpMAoGCCqGSM49BAMDA0kAMEYCIQCbu5oUoM62GHMHpqVcQ/Zvu3Xn
0fY7htFCNNMtPRfTfAIhAKiYFlYwJAX7vyTRv7R0dag0KNTp3gPg6qQuDwNhCHwt
-----END CERTIFICATE-----"""
    }
}
