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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
