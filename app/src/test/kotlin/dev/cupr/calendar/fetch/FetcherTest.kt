package dev.cupr.calendar.fetch

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.FakeHomeAssistant
import dev.cupr.calendar.ha.RefreshToken
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.AesGcmCipher
import dev.cupr.calendar.store.FailedFetch
import dev.cupr.calendar.store.FailedFetchStore
import dev.cupr.calendar.store.FetchCause
import dev.cupr.calendar.store.Snapshot
import dev.cupr.calendar.store.SnapshotStore
import dev.cupr.calendar.store.TokenCipher
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.security.ProviderException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class FetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val account = Account(prefs, AesGcmCipher { key })
    private val snapshotFile = File(context.filesDir, "snapshot.json")
    private val snapshots = SnapshotStore(snapshotFile)
    private val failures = FailedFetchStore(prefs)
    private val now = Instant.parse("2026-11-12T09:30:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))
    private val fetcher = fetcher(account)
    private val old = Snapshot(1, "Daniel", emptyList())

    private val good =
        """{"start":"2026-11-14","end":"2026-11-15","all_day":true,"title":"Anna","calendar":"calendar.contact_birthdays"}"""
    private val bad = """{"start":"2026-11-14","all_day":true,"title":"Broken","calendar":"c"}"""

    @After
    fun stop() = home.close()

    /** Another Fetcher on the same files, as the app makes one for each use. */
    private var online = true

    private fun fetcher(account: Account) = Fetcher(account, snapshots, failures, OkHttpClient(), clock) { online }

    private val code = AuthorizationCode("code-1")

    private fun cipher(open: () -> Nothing) =
        object : TokenCipher {
            override fun seal(plain: ByteArray) = AesGcmCipher { key }.seal(plain)

            override fun open(sealed: ByteArray): ByteArray = open()
        }

    private fun signIn() = account.signIn(home.address, "http://127.0.0.1:47123/", RefreshToken("refresh-1"))

    private fun entries(vararg json: String) {
        home.states = {
            FakeHomeAssistant.json(
                """{"entity_id":"sensor.family_calendar","attributes":{"entries":[${json.joinToString(",")}]}}""",
            )
        }
    }

    private val logs: String
        get() = ShadowLog.getLogsForTag("Fetch").joinToString("\n") { it.msg }

    @Test
    fun `a fetch replaces the snapshot with the entries, the user and the theme`() {
        signIn()
        entries(good, bad)

        val outcome = fetcher.fetch()

        assertEquals(Fetcher.Outcome.Fetched(entries = 1, leftOut = 1), outcome)
        val snapshot = snapshots.read()!!
        assertEquals(now, snapshot.fetchedAt)
        assertEquals("Daniel", snapshot.user)
        assertEquals(listOf("Anna"), snapshot.entries(ZoneId.of("Europe/Berlin")).map { it.title })
        assertEquals("#1f6fd6", (snapshot.theme!!["primary-color"] as JsonPrimitive).content)
        assertTrue(logs, logs.contains("Entry 1 (\"Broken\") left out:"))
        val form = home.requests.first().second
        assertEquals("refresh-1", form["refresh_token"])
        assertEquals("http://127.0.0.1:47123/", form["client_id"])
    }

    @Test
    fun `no token lands in the preferences or the snapshot`() {
        signIn()
        entries(good)

        fetcher.fetch()

        val stored = prefs.all.values.joinToString() + snapshotFile.readText()
        assertFalse(stored, stored.contains("refresh-1"))
        assertFalse(stored, stored.contains("access-1"))
    }

    @Test
    fun `a refused refresh token marks the sign-in as refused and keeps the snapshot`() {
        signIn()
        snapshots.write(old)
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }

        assertEquals(Fetcher.Outcome.Rejected, fetcher.fetch())

        assertEquals(Account.State.REJECTED, account.state)
        assertEquals(old, snapshots.read())
        assertEquals(1, home.requests.size)
        assertTrue(logs, logs.contains("Home Assistant refused the refresh token"))
    }

    @Test
    fun `a failed fetch changes nothing but the last failure`() {
        signIn()
        snapshots.write(old)
        home.states = { FakeHomeAssistant.json("""{"message":"Server error"}""", 500) }

        val reason = "HTTP 500 for sensor.family_calendar"
        assertEquals(Fetcher.Outcome.Failed(reason, FetchCause.UNEXPECTED), fetcher.fetch())

        assertEquals(Account.State.SIGNED_IN, account.state)
        assertEquals(old, snapshots.read())
        assertEquals(FailedFetch(now, FetchCause.UNEXPECTED), failures.read())
    }

    @Test
    fun `a fetch that succeeds forgets the last failure`() {
        signIn()
        failures.write(FailedFetch(Instant.EPOCH, FetchCause.UNREACHABLE))

        fetcher.fetch()

        assertNull(failures.read())
    }

    @Test
    fun `a refresh that gets no answer is a failure, not a refusal`() {
        signIn()
        home.token = { FakeHomeAssistant.json("Bad Gateway", 502) }

        assertEquals(Fetcher.Outcome.Failed("refresh: HTTP 502", FetchCause.UNREACHABLE), fetcher.fetch())

        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `a WebSocket that fails keeps the old snapshot`() {
        signIn()
        snapshots.write(old)
        entries(good)
        home.themes = "[]"

        assertEquals(
            Fetcher.Outcome.Failed("WebSocket: an answer without user or themes", FetchCause.UNEXPECTED),
            fetcher.fetch(),
        )

        assertEquals(old, snapshots.read())
        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `a snapshot that cannot be written is a failure`() {
        signIn()
        entries(good)
        // A directory where AtomicFile writes the new snapshot first: it cannot open its file there.
        File(snapshotFile.path + ".new", "blocking").mkdirs()

        val outcome = fetcher.fetch()

        assertTrue(outcome.toString(), outcome.toString().startsWith("Failed(reason=the snapshot cannot be written: "))
        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    @Test
    fun `without a sign-in nothing is fetched`() {
        assertEquals(Fetcher.Outcome.NotSignedIn, fetcher.fetch())

        assertEquals(0, home.requests.size)
    }

    @Test
    fun `a stored token that cannot be read on this phone marks the sign-in unreadable, not refused`() {
        signIn()
        snapshots.write(old)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        assertEquals(Fetcher.Outcome.Unreadable, fetcher(Account(prefs, AesGcmCipher { other })).fetch())

        assertEquals(Account.State.UNREADABLE, account.state)
        assertEquals(old, snapshots.read())
        assertEquals(0, home.requests.size)
        assertTrue(logs, logs.contains("The stored sign-in cannot be read on this phone"))
        assertFalse(logs, logs.contains("refused"))
    }

    @Test
    fun `a Keystore that fails is a failed fetch, and the sign-in stays`() {
        signIn()
        val failing = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })

        val reason = "the Keystore failed: ProviderException"
        assertEquals(Fetcher.Outcome.Failed(reason, FetchCause.PHONE), fetcher(failing).fetch())

        assertEquals(Account.State.SIGNED_IN, account.state)
        assertEquals(0, home.requests.size)
        assertEquals(FailedFetch(now, FetchCause.PHONE), failures.read())
    }

    @Test
    fun `a sign-in trades the code with the verifier whose challenge the login page had, and fetches nothing yet`() {
        val request = SignInRequest.start(home.address)
        home.challenge = request.authorizeUrl.queryParameter("code_challenge")
        snapshots.write(old)
        failures.write(FailedFetch(Instant.EPOCH, FetchCause.UNREACHABLE))

        assertEquals(Fetcher.SignIn.SignedIn, fetcher.signIn(request, code))

        val session = (account.open() as Account.Opened.Usable).session
        assertEquals(RefreshToken("refresh-1"), session.refreshToken)
        assertEquals(SignInRequest.CLIENT_ID, session.clientId)
        assertEquals(home.address, session.address)
        assertNull(snapshots.read())
        assertNull(failures.read())
        assertEquals("code-1", home.requests.single().second["code"])
    }

    @Test
    fun `a sign-in with another verifier than the challenge's keeps nothing`() {
        val request = SignInRequest.start(home.address)
        home.challenge = SignInRequest.challenge("another verifier, long enough to be one: 0123456789")
        snapshots.write(old)

        assertEquals(
            Fetcher.SignIn.Failed("Home Assistant gave no tokens: HTTP 400 invalid_grant"),
            fetcher.signIn(request, code),
        )

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(old, snapshots.read())
    }

    @Test
    fun `a token that cannot be sealed is ended in Home Assistant again and nothing is kept`() {
        val failing = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })
        snapshots.write(old)

        val outcome = fetcher(failing).signIn(SignInRequest.start(home.address), code)

        assertEquals(
            Fetcher.SignIn.Failed(
                "the token cannot be sealed (ProviderException); Home Assistant ended the refresh token",
            ),
            outcome,
        )
        val (revoke, form) = home.requests.last()
        assertEquals("/auth/revoke", revoke.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(old, snapshots.read())
    }

    @Test
    fun `signing out ends the refresh token, forgets it and deletes the snapshot and the last failure`() {
        signIn()
        snapshots.write(old)
        failures.write(FailedFetch(Instant.EPOCH, FetchCause.UNREACHABLE))

        fetcher.signOut()

        val (request, form) = home.requests.single()
        assertEquals("/auth/revoke", request.url.encodedPath)
        assertEquals("refresh-1", form["token"])
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(home.address, account.address)
        assertNull(snapshots.read())
        assertNull(failures.read())
    }

    @Test(timeout = 10_000)
    fun `signing out waits for a running fetch, so its snapshot does not outlive the sign-in`() {
        signIn()
        entries(good)
        val gate = CountDownLatch(1)
        home.statesGate = gate
        val pool = Executors.newFixedThreadPool(2)
        try {
            val fetch = pool.submit<Fetcher.Outcome> { fetcher(account).fetch() }
            assertTrue(home.statesAsked.await(5, TimeUnit.SECONDS))
            val signOut = pool.submit { fetcher(account).signOut() }
            // The sign-out has asked Home Assistant to end the token and now waits for the fetch.
            Thread.sleep(SETTLE_MILLIS)
            assertFalse(signOut.isDone)
            gate.countDown()

            assertEquals(Fetcher.Outcome.Fetched(entries = 1, leftOut = 0), fetch.get(5, TimeUnit.SECONDS))
            signOut.get(5, TimeUnit.SECONDS)
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }

        assertNull(snapshots.read())
        assertEquals(Account.State.SIGNED_OUT, account.state)
    }

    @Test
    fun `signing out without an answer from Home Assistant still signs out`() {
        signIn()
        home.close()

        fetcher.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertTrue(logs, logs.contains("could not be asked to end the refresh token (ConnectException)"))
    }

    @Test
    fun `signing out with a stored token that cannot be read signs out and says Home Assistant was not asked`() {
        signIn()
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        fetcher(Account(prefs, AesGcmCipher { other })).signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(0, home.requests.size)
        assertTrue(logs, logs.contains("so Home Assistant was not asked to end it"))
    }

    @Test
    fun `signing out with a Keystore that fails signs out and says Home Assistant was not asked`() {
        signIn()

        fetcher(Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })).signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(0, home.requests.size)
        assertTrue(logs, logs.contains("the stored token cannot be read (the Keystore failed: ProviderException)"))
    }

    @Test
    fun `signing out signs out even when opening the stored sign-in throws`() {
        signIn()
        snapshots.write(old)
        val throwing = Account(prefs, cipher { throw IllegalStateException("broken") })

        runCatching { fetcher(throwing).signOut() }

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertNull(snapshots.read())
        assertTrue(logs, logs.contains("Signed out; Home Assistant was not asked to end the refresh token"))
    }

    @Test
    fun `no token, code or address shows in the log, on success or failure`() {
        signIn()
        entries(good, bad)
        fetcher.fetch()
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        fetcher.fetch()
        signIn()
        fetcher.signIn(SignInRequest.start(home.address), code)
        home.close()
        fetcher.fetch()
        fetcher.signOut()

        val all = ShadowLog.getLogs().joinToString("\n") { it.msg + it.throwable }
        // An exception's message names the host and its port ("localhost/127.0.0.1:41234"); the port alone is enough.
        for (secret in listOf("refresh-1", "access-1", "code-1", ":${home.url.port}")) {
            assertFalse(secret, all.contains(secret))
        }
    }

    @Test
    fun `what failed is named for the screen in plain causes`() {
        signIn()
        home.states = { FakeHomeAssistant.json("""{"message":"Entity not found."}""", 404) }
        assertEquals(FetchCause.SENSOR_MISSING, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)

        home.accessToken = "another"
        home.token = { FakeHomeAssistant.json("""{"access_token":"access-1","token_type":"Bearer"}""") }
        assertEquals(FetchCause.REFUSED, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)

        home.close()
        assertEquals(FetchCause.UNREACHABLE, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
        online = false
        assertEquals(FetchCause.NO_NETWORK, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
    }

    @Test(timeout = 10_000)
    fun `a timeout is named as such, or as no network while the phone has none`() {
        signIn()
        val gate = CountDownLatch(1)
        home.statesGate = gate
        val impatient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
        val fetcher = Fetcher(account, snapshots, failures, impatient, clock) { online }
        try {
            assertEquals(FetchCause.TIMEOUT, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
            online = false
            assertEquals(FetchCause.NO_NETWORK, (fetcher.fetch() as Fetcher.Outcome.Failed).cause)
        } finally {
            gate.countDown()
        }
    }

    @Test
    fun `a snapshot that does not end up in place is a failure of the phone`() {
        signIn()
        entries(good)
        File(snapshotFile, "blocking").mkdirs()

        val outcome = fetcher.fetch()

        assertEquals(FetchCause.PHONE, (outcome as Fetcher.Outcome.Failed).cause)
    }

    @Test(timeout = 10_000)
    fun `a new sign-in waits for a running fetch, so its snapshot does not outlive the sign-in`() {
        signIn()
        entries(good)
        val gate = CountDownLatch(1)
        home.statesGate = gate
        val pool = Executors.newFixedThreadPool(2)
        try {
            val fetch = pool.submit<Fetcher.Outcome> { fetcher(account).fetch() }
            assertTrue(home.statesAsked.await(5, TimeUnit.SECONDS))
            val signIn =
                pool.submit<Fetcher.SignIn> {
                    fetcher(
                        account,
                    ).signIn(SignInRequest.start(home.address), code)
                }
            Thread.sleep(SETTLE_MILLIS)
            assertFalse(signIn.isDone)
            gate.countDown()

            assertEquals(Fetcher.Outcome.Fetched(entries = 1, leftOut = 0), fetch.get(5, TimeUnit.SECONDS))
            assertEquals(Fetcher.SignIn.SignedIn, signIn.get(5, TimeUnit.SECONDS))
        } finally {
            gate.countDown()
            pool.shutdownNow()
        }

        assertNull(snapshots.read())
    }

    @Test(timeout = 10_000)
    fun `without a sign-in, what stops the fetches runs before a waiting sign-in is kept`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            var stateWhileStopping: Account.State? = null
            val outcome =
                fetcher.fetch {
                    val signIn = pool.submit { fetcher(account).signIn(SignInRequest.start(home.address), code) }
                    Thread.sleep(SETTLE_MILLIS)
                    stateWhileStopping = account.state
                    assertFalse(signIn.isDone)
                }

            assertEquals(Fetcher.Outcome.NotSignedIn, outcome)
            assertEquals(Account.State.SIGNED_OUT, stateWhileStopping)
        } finally {
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }
        assertEquals(Account.State.SIGNED_IN, account.state)
    }

    private companion object {
        /** Long enough for the sign-out to reach the lock, were there none. */
        const val SETTLE_MILLIS = 300L
    }
}
