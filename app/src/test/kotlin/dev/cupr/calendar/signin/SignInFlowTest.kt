package dev.cupr.calendar.signin

import android.app.Application
import android.util.Log
import androidx.compose.runtime.saveable.SaverScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.FakeHomeAssistant
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.AesGcmCipher
import dev.cupr.calendar.store.TokenCipher
import dev.cupr.calendar.widget.DrawnWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class SignInFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var keystoreCipher: () -> TokenCipher
    private val code = AuthorizationCode("code-1")

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.WARN)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
    }

    @After
    fun tearDown() {
        home.tokenGate?.countDown()
        awaitIdle()
        Services.cipher = keystoreCipher
        WorkManagerTestInitHelper.closeWorkDatabase()
        home.close()
        SignInFlow.clearProblem()
    }

    private fun awaitIdle() = runBlocking { withTimeout(5_000) { SignInFlow.state.first { !it.busy } } }

    /** Starts a run that is done when [start] returns, its failure handled by then too. */
    private fun atOnce(start: () -> Boolean): Boolean {
        val io = Services.io
        Services.io = Dispatchers.Unconfined
        try {
            return start()
        } finally {
            Services.io = io
        }
    }

    @Test(timeout = 10_000)
    fun `a second sign-in while one runs is refused, and the result stays held after the run`() {
        val gate = CountDownLatch(1)
        home.tokenGate = gate
        val finished = SignInFlow.state.value.finished

        assertTrue(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        assertTrue(home.tokenAsked.await(5, TimeUnit.SECONDS))
        assertTrue(SignInFlow.state.value.busy)
        assertFalse(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        assertFalse(SignInFlow.signOut(context))
        gate.countDown()
        awaitIdle()

        assertEquals(finished + 1, SignInFlow.state.value.finished)
        assertNull(SignInFlow.state.value.problem)
        assertEquals(Account.State.SIGNED_IN, Services.account(context).state)
        assertEquals(1, home.requests.count { it.first.url.encodedPath == "/auth/token" })
    }

    @Test(timeout = 10_000)
    fun `a sign-in Home Assistant refuses leaves its problem for the screen`() {
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }

        assertTrue(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        awaitIdle()

        assertEquals(Problem.FAILED, SignInFlow.state.value.problem)
        assertEquals(Account.State.SIGNED_OUT, Services.account(context).state)
    }

    @Test(timeout = 10_000)
    fun `a new run clears the problem of the one before, and a refused start is logged`() {
        SignInFlow.fail(Problem.UNREACHABLE)
        val gate = CountDownLatch(1)
        home.tokenGate = gate

        assertTrue(SignInFlow.signIn(context, SignInRequest.start(home.address), code))
        assertNull(SignInFlow.state.value.problem)
        assertTrue(home.tokenAsked.await(5, TimeUnit.SECONDS))
        assertFalse(SignInFlow.signOut(context))
        gate.countDown()
        awaitIdle()

        val logs = ShadowLog.getLogsForTag(SIGN_IN_TAG).joinToString { it.msg }
        assertTrue(logs, logs.contains("A sign-in or sign-out is under way; another was not started"))
    }

    @Test(timeout = 10_000)
    fun `a run that throws a runtime exception ends, says the sign-in failed and logs the exception's class`() {
        Services.cipher = { throw IllegalStateException("broken") }

        assertTrue(atOnce { SignInFlow.signOut(context) })

        assertFalse(SignInFlow.state.value.busy)
        assertEquals(Problem.FAILED, SignInFlow.state.value.problem)
        val errors = ShadowLog.getLogsForTag(SIGN_IN_TAG).filter { it.type == Log.ERROR }.map { it.msg }
        assertEquals(listOf("A sign-in or sign-out failed: IllegalStateException"), errors)
    }

    @Test(timeout = 10_000)
    fun `a sign-in kept whose widget update then throws ends and says the sign-in failed`() {
        val content = DrawnWidgetProvider.content
        DrawnWidgetProvider.content = { throw IllegalStateException("the widgets cannot be read") }
        try {
            assertTrue(atOnce { SignInFlow.signIn(context, SignInRequest.start(home.address), code) })
        } finally {
            DrawnWidgetProvider.content = content
        }

        assertEquals(Account.State.SIGNED_IN, Services.account(context).state)
        assertFalse(SignInFlow.state.value.busy)
        assertEquals(Problem.FAILED, SignInFlow.state.value.problem)
    }

    /** Runs a sign-out that throws a [StackOverflowError]; what reached the thread's uncaught-exception handler. */
    private fun signOutThrowingAnError(): List<Throwable> {
        Services.cipher = { throw StackOverflowError() }
        val thread = Thread.currentThread()
        val handler = thread.uncaughtExceptionHandler
        val uncaught = mutableListOf<Throwable>()
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            assertTrue(atOnce { SignInFlow.signOut(context) })
        } finally {
            thread.uncaughtExceptionHandler = handler
        }
        return uncaught
    }

    @Test(timeout = 10_000)
    fun `an error a run throws is not taken for a failed sign-in and reaches the thread's handler`() {
        val uncaught = signOutThrowingAnError()

        assertEquals(listOf(StackOverflowError::class.java), uncaught.map { it.javaClass })
        assertFalse(SignInFlow.state.value.busy)
        assertNull(SignInFlow.state.value.problem)
    }

    /**
     * A `runTest`, which Compose's test rule runs too, switches kotlinx-coroutines-test's collector of uncaught
     * exceptions on for the whole sandbox; an error that reached it would be kept and fail the next `runTest`.
     */
    @Test(timeout = 10_000)
    fun `an error a run throws reaches no coroutine test that starts after it`() {
        runTest { }

        assertEquals(listOf(StackOverflowError::class.java), signOutThrowingAnError().map { it.javaClass })
        runTest { }
    }

    @Test
    fun `a pending sign-in survives a configuration change`() {
        val request = SignInRequest.start(HaAddress.parse("https://home.example")!!)

        val saved = with(RequestSaver) { SaverScope { true }.save(request) }!!
        val restored = RequestSaver.restore(saved)!!

        assertEquals(request.address, restored.address)
        assertEquals(request.authorizeUrl, restored.authorizeUrl)
        assertEquals(
            "https://home.example/".toHttpUrl(),
            restored.address.url,
        )
    }
}
