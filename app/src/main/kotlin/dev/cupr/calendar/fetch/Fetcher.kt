package dev.cupr.calendar.fetch

import android.util.Log
import dev.cupr.calendar.ha.AccessToken
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.EntryParser
import dev.cupr.calendar.ha.FailureKind
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.ha.HaClient
import dev.cupr.calendar.ha.RefreshToken
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.ha.TokenClient
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.CipherFailureException
import dev.cupr.calendar.store.FailedFetch
import dev.cupr.calendar.store.FailedFetchStore
import dev.cupr.calendar.store.FetchCause
import dev.cupr.calendar.store.Snapshot
import dev.cupr.calendar.store.SnapshotStore
import kotlinx.serialization.json.JsonArray
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Clock

/**
 * The sign-in's lifetime with Home Assistant: keeping a new sign-in, each
 * fetch, and signing out (D45, D46, D47).
 *
 * A fetch asks for a new access token with the stored refresh token, reads
 * the sensor's entries (D16), the user's name and the theme "Zuhause"
 * (D17), and replaces the snapshot whole (D6). When Home Assistant refuses
 * the refresh token, the account is marked [Account.State.REJECTED]; when
 * the stored sign-in cannot be read on this phone, [Account.State.UNREADABLE];
 * the snapshot stays in both cases. Any other failure changes nothing but
 * the last failure kept for the sign-in screen, and the next fetch tries
 * again. Entries that cannot be read are left out and logged, one line
 * each; the rest are used.
 *
 * The log names what failed, never a token, the code or the address.
 */
class Fetcher(
    private val account: Account,
    private val snapshots: SnapshotStore,
    private val failures: FailedFetchStore,
    private val http: OkHttpClient,
    private val clock: Clock,
    /** Whether the phone has a network now; a fetch that cannot reach Home Assistant without one says so. */
    private val online: () -> Boolean,
) {
    sealed interface Outcome {
        data object NotSignedIn : Outcome

        data object Rejected : Outcome

        data object Unreadable : Outcome

        /** [reason] for the log, [cause] for the screen; neither holds a secret. */
        data class Failed(
            val reason: String,
            val cause: FetchCause,
        ) : Outcome

        data class Fetched(
            val entries: Int,
            val leftOut: Int,
        ) : Outcome
    }

    sealed interface SignIn {
        data object SignedIn : SignIn

        /** The sign-in did not come about; [reason] holds no secret. */
        data class Failed(
            val reason: String,
        ) : SignIn
    }

    /**
     * Trades the code [request]'s redirect carried for a refresh token and
     * keeps it; the snapshot and the last failure of an earlier sign-in go
     * (D45). When the token cannot be sealed, Home Assistant is asked to end
     * it again, so it is not left valid there unused, and nothing is kept.
     */
    fun signIn(
        request: SignInRequest,
        code: AuthorizationCode,
    ): SignIn {
        val tokens = TokenClient(http, request.address)
        val outcome =
            when (val exchange = tokens.exchange(code, request)) {
                is TokenClient.Exchange.Failed -> SignIn.Failed("Home Assistant gave no tokens: ${exchange.reason}")
                is TokenClient.Exchange.Signed -> keep(request.address, exchange.refreshToken, tokens)
            }
        when (outcome) {
            SignIn.SignedIn -> Log.i(TAG, "Signed in")
            is SignIn.Failed -> Log.w(TAG, "Sign-in failed: ${outcome.reason}")
        }
        return outcome
    }

    private fun keep(
        address: HaAddress,
        refreshToken: RefreshToken,
        tokens: TokenClient,
    ): SignIn =
        try {
            synchronized(LOCK) {
                account.signIn(address, SignInRequest.CLIENT_ID, refreshToken)
                snapshots.clear()
                failures.clear()
            }
            SignIn.SignedIn
        } catch (e: CipherFailureException) {
            SignIn.Failed("the token cannot be sealed (${e.message}); ${revoked(tokens.revoke(refreshToken))}")
        }

    /**
     * One fetch. Without a usable sign-in, [onNoSignIn] runs before the lock
     * is released, so a sign-in waiting for it, which schedules the fetches
     * anew, comes after whatever [onNoSignIn] stops.
     */
    fun fetch(onNoSignIn: () -> Unit = {}): Outcome =
        synchronized(LOCK) {
            val outcome = run()
            when (outcome) {
                is Outcome.Failed -> failures.write(FailedFetch(clock.instant(), outcome.cause))
                is Outcome.Fetched -> failures.clear()
                Outcome.NotSignedIn, Outcome.Rejected, Outcome.Unreadable -> onNoSignIn()
            }
            log(outcome)
            outcome
        }

    private fun log(outcome: Outcome) {
        when (outcome) {
            is Outcome.Failed -> {
                Log.w(TAG, "Fetch failed: ${outcome.reason}")
            }

            is Outcome.Fetched -> {
                Log.i(TAG, "Fetched ${outcome.entries} entries, ${outcome.leftOut} left out")
            }

            Outcome.Rejected -> {
                Log.w(TAG, "Home Assistant refused the refresh token; signing in again is needed")
            }

            Outcome.Unreadable -> {
                Log.w(
                    TAG,
                    "The stored sign-in cannot be read on this phone; signing in again is needed",
                )
            }

            Outcome.NotSignedIn -> {
                Log.i(TAG, "Not signed in, nothing fetched")
            }
        }
    }

    /**
     * Signs out ("Abmelden", D47): asks Home Assistant to end the refresh
     * token, then forgets the token and deletes the snapshot and the last
     * failure; the address stays. Waits for a fetch that is running, so it
     * cannot write its snapshot afterwards. The local sign-in goes even when
     * asking Home Assistant fails or the stored token cannot be read.
     */
    fun signOut() {
        var asked = "Home Assistant was not asked to end the refresh token"
        try {
            asked =
                when (val opened = account.open()) {
                    Account.Opened.NotSignedIn -> {
                        "nothing was stored"
                    }

                    is Account.Opened.Usable -> {
                        revoked(
                            TokenClient(http, opened.session.address).revoke(opened.session.refreshToken),
                        )
                    }

                    is Account.Opened.Unreadable -> {
                        notAsked(opened.reason)
                    }

                    is Account.Opened.Unavailable -> {
                        notAsked(opened.reason)
                    }
                }
        } finally {
            synchronized(LOCK) {
                account.signOut()
                snapshots.clear()
                failures.clear()
            }
            Log.i(TAG, "Signed out; $asked")
        }
    }

    private fun revoked(revoke: TokenClient.Revoke): String =
        when (revoke) {
            TokenClient.Revoke.Ended -> {
                "Home Assistant ended the refresh token"
            }

            is TokenClient.Revoke.Failed -> {
                "Home Assistant could not be asked to end the refresh token (${revoke.reason}); $UNTIL_UNUSED"
            }
        }

    private fun notAsked(reason: String) =
        "the stored token cannot be read ($reason), so Home Assistant was not asked to end it; $UNTIL_UNUSED"

    private fun run(): Outcome =
        when (val opened = account.open()) {
            Account.Opened.NotSignedIn -> {
                Outcome.NotSignedIn
            }

            is Account.Opened.Unreadable -> {
                Log.w(TAG, "The stored sign-in cannot be read: ${opened.reason}")
                account.markUnreadable()
                Outcome.Unreadable
            }

            is Account.Opened.Unavailable -> {
                Outcome.Failed(opened.reason, FetchCause.PHONE)
            }

            is Account.Opened.Usable -> {
                refresh(opened.session)
            }
        }

    private fun refresh(session: Account.Session): Outcome =
        when (val refresh = TokenClient(http, session.address).refresh(session.refreshToken, session.clientId)) {
            is TokenClient.Refresh.Access -> {
                read(HaClient(http, session.address), refresh.accessToken)
            }

            is TokenClient.Refresh.Rejected -> {
                Log.w(TAG, "The refresh token was refused: ${refresh.reason}")
                account.reject()
                Outcome.Rejected
            }

            is TokenClient.Refresh.Failed -> {
                Outcome.Failed("refresh: ${refresh.reason}", causeOf(refresh.kind, online))
            }
        }

    private fun read(
        client: HaClient,
        accessToken: AccessToken,
    ): Outcome =
        when (val entries = client.entries(accessToken)) {
            is HaClient.Result.Failed -> {
                Outcome.Failed(entries.reason, causeOf(entries.kind, online))
            }

            is HaClient.Result.Ok -> {
                when (val userAndTheme = client.userAndTheme(accessToken)) {
                    is HaClient.Result.Failed -> Outcome.Failed(userAndTheme.reason, causeOf(userAndTheme.kind, online))
                    is HaClient.Result.Ok -> store(entries.value, userAndTheme.value)
                }
            }
        }

    private fun store(
        entries: JsonArray,
        userAndTheme: HaClient.UserAndTheme,
    ): Outcome {
        if (userAndTheme.theme == null) Log.w(TAG, "Home Assistant has no theme \"${HaClient.THEME}\"")
        val parsed = EntryParser.parse(entries, clock.zone)
        parsed.problems.forEach { Log.w(TAG, it) }
        return try {
            snapshots.write(
                Snapshot(clock.millis(), userAndTheme.user, parsed.read.map { it.json }, userAndTheme.theme),
            )
            Outcome.Fetched(parsed.read.size, entries.size - parsed.read.size)
        } catch (e: IOException) {
            Outcome.Failed("the snapshot cannot be written: ${e.javaClass.simpleName}", FetchCause.PHONE)
        }
    }

    private companion object {
        const val TAG = "Fetch"

        /** Home Assistant's refresh tokens expire after 90 days unused (`auth/const.py` at Core 2026.11.1). */
        const val UNTIL_UNUSED = "it stays valid there until it goes 90 days unused"

        /**
         * One lock for every Fetcher, since the app makes a new one for each
         * use: a periodic fetch, one started at once, a sign-in and a sign-out
         * write the account and the snapshot one after the other.
         */
        val LOCK = Any()
    }
}

/**
 * The screen's cause for what failed (D46): Home Assistant out of reach
 * while the phone has no network is the network's.
 */
private fun causeOf(
    kind: FailureKind,
    online: () -> Boolean,
): FetchCause =
    when (kind) {
        FailureKind.UNREACHABLE -> if (online()) FetchCause.UNREACHABLE else FetchCause.NO_NETWORK
        FailureKind.TIMEOUT -> if (online()) FetchCause.TIMEOUT else FetchCause.NO_NETWORK
        FailureKind.NOT_FOUND -> FetchCause.SENSOR_MISSING
        FailureKind.REFUSED -> FetchCause.REFUSED
        FailureKind.UNEXPECTED -> FetchCause.UNEXPECTED
    }
