package dev.cupr.calendar.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.VisibleForTesting
import dev.cupr.calendar.fetch.FetchWork
import dev.cupr.calendar.fetch.Fetcher
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.AesGcmCipher
import dev.cupr.calendar.store.FailedFetchStore
import dev.cupr.calendar.store.KeystoreKey
import dev.cupr.calendar.store.SnapshotStore
import dev.cupr.calendar.store.TokenCipher
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * The app's one sign-in, snapshot and HTTP client, and what the sign-in
 * screen and the fetch do with them, with the fetches scheduled to match
 * (D46, D47).
 */
object Services {
    private const val ACCOUNT = "account"
    private const val SNAPSHOT = "snapshot.json"
    private const val CALL_TIMEOUT_SECONDS = 30L

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    }

    /** The refresh token's cipher; tests replace it, since Robolectric has no Android Keystore. */
    @VisibleForTesting
    var cipher: () -> TokenCipher = { AesGcmCipher(KeystoreKey::get) }

    fun account(context: Context) = Account(prefs(context), cipher())

    fun snapshots(context: Context) = SnapshotStore(File(context.filesDir, SNAPSHOT))

    fun failures(context: Context) = FailedFetchStore(prefs(context))

    fun fetcher(context: Context) =
        Fetcher(account(context), snapshots(context), failures(context), http, Clock.systemDefaultZone()) {
            online(context)
        }

    /** Whether the phone has a network that reaches the internet, as the system sees it now. */
    internal fun online(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Keeps a new sign-in and fetches at once (D46); true when signed in. */
    fun signIn(
        context: Context,
        request: SignInRequest,
        code: AuthorizationCode,
    ): Boolean {
        val signedIn = fetcher(context).signIn(request, code) == Fetcher.SignIn.SignedIn
        if (signedIn) FetchWork.start(context)
        return signedIn
    }

    /**
     * Signs out (D47). The fetches stop before and again after it, so a
     * fetch scheduled while it waits for a running one, when the screen
     * opens, does not outlive it.
     */
    fun signOut(context: Context) {
        FetchWork.stop(context)
        fetcher(context).signOut()
        FetchWork.stop(context)
    }

    /** The app's screen opened: while signed in, it fetches at once and keeps the periodic fetch (D46). */
    fun opened(context: Context) {
        if (account(context).state == Account.State.SIGNED_IN) FetchWork.start(context)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(ACCOUNT, Context.MODE_PRIVATE)
}
