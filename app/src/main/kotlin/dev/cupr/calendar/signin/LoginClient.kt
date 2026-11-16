package dev.cupr.calendar.signin

import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.cupr.calendar.ha.SignInRequest

/**
 * The login page's WebView client (D43). The redirect to the app's
 * redirect URI is caught before it loads and handed to [onCallback], as is
 * a redirect this sign-in cannot use; every other page loads as usual. When
 * the page itself fails to load, Home Assistant answers it with an HTTP
 * error, or its TLS fails, [onUnreachable] ends the sign-in; the log names
 * the error's code. No URL is logged: the redirect carries the code, and
 * the others the address.
 */
class LoginClient(
    private val signIn: SignInRequest,
    private val onCallback: (SignInRequest.Callback) -> Unit,
    private val onUnreachable: () -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val callback = signIn.callback(request.url.toString())
        if (callback == SignInRequest.Callback.NotOurs) return false
        onCallback(callback)
        return true
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) = failed(request, "load error ${error.errorCode}")

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) = failed(request, "HTTP ${errorResponse.statusCode}")

    /**
     * A certificate the WebView does not trust (a captive portal, an expired
     * or self-signed one, a TLS proxy): the load is cancelled, never let
     * through, and the sign-in ends, for any part of the page, since they
     * all come from Home Assistant's host.
     */
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        unreachable("TLS error ${error.primaryError}")
    }

    /** Only the page itself counts; an image or script that fails does not end the sign-in. */
    private fun failed(
        request: WebResourceRequest,
        cause: String,
    ) {
        if (request.isForMainFrame) unreachable(cause)
    }

    private fun unreachable(cause: String) {
        Log.w(SIGN_IN_TAG, "The login page did not load: $cause")
        onUnreachable()
    }
}
