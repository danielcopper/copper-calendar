package dev.cupr.calendar.fetch

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.ha.AuthorizationCode
import dev.cupr.calendar.ha.FakeHomeAssistant
import dev.cupr.calendar.ha.RefreshToken
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.store.Account
import dev.cupr.calendar.store.AesGcmCipher
import dev.cupr.calendar.store.TokenCipher
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class FetchWorkTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val home = FakeHomeAssistant()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var keystoreCipher: () -> TokenCipher
    private val manager get() = WorkManager.getInstance(context)

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
        Services.cipher = keystoreCipher
        WorkManagerTestInitHelper.closeWorkDatabase()
        home.close()
    }

    private fun work() = TestListenableWorkerBuilder<FetchWorker>(context).build().doWork()

    private fun signIn() =
        Services.account(context).signIn(home.address, SignInRequest.CLIENT_ID, RefreshToken("refresh-1"))

    private fun states(name: String) = manager.getWorkInfosForUniqueWork(name).get().map { it.state }

    private val scheduled get() = states(FetchWork.NOW) + states(FetchWork.PERIODIC)

    @Test
    fun `the worker fetches into the snapshot and succeeds`() {
        signIn()

        assertEquals(ListenableWorker.Result.success(), work())

        assertEquals("Daniel", Services.snapshots(context).read()!!.user)
    }

    @Test
    fun `a failed fetch fails the work and keeps the periodic fetch`() {
        signIn()
        FetchWork.start(context)
        home.close()

        assertEquals(ListenableWorker.Result.failure(), work())

        assertEquals(listOf(WorkInfo.State.ENQUEUED), states(FetchWork.PERIODIC))
    }

    @Test
    fun `without a usable sign-in the work succeeds and the periodic fetch stops`() {
        FetchWork.start(context)
        assertEquals(ListenableWorker.Result.success(), work())
        assertEquals(listOf(WorkInfo.State.CANCELLED), states(FetchWork.PERIODIC))

        signIn()
        FetchWork.start(context)
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        assertEquals(ListenableWorker.Result.success(), work())
        assertEquals(listOf(WorkInfo.State.CANCELLED), states(FetchWork.PERIODIC).distinct())
    }

    @Test
    fun `an unreadable stored sign-in stops the periodic fetch too`() {
        signIn()
        FetchWork.start(context)
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        Services.cipher = { AesGcmCipher { other } }

        assertEquals(ListenableWorker.Result.success(), work())

        assertEquals(listOf(WorkInfo.State.CANCELLED), states(FetchWork.PERIODIC))
    }

    @Test
    fun `the fetch at once is expedited where the quota allows`() {
        val request = FetchWork.nowRequest()

        assertTrue(request.workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
    }

    @Test
    fun `starting fetches at once and every 15 minutes, each only with a network`() {
        FetchWork.start(context)

        val now = manager.getWorkInfosForUniqueWork(FetchWork.NOW).get().single()
        val periodic = manager.getWorkInfosForUniqueWork(FetchWork.PERIODIC).get().single()
        assertEquals(NetworkType.CONNECTED, now.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        assertNotNull(periodic.periodicityInfo)
        assertEquals(TimeUnit.MINUTES.toMillis(15), periodic.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(null, now.periodicityInfo)
    }

    @Test
    fun `starting again keeps the periodic fetch as it is`() {
        FetchWork.start(context)
        val first =
            manager
                .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
                .get()
                .single()
                .id

        FetchWork.start(context)

        assertEquals(
            first,
            manager
                .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
                .get()
                .single()
                .id,
        )
    }

    @Test
    fun `the screen opening fetches only while signed in`() {
        Services.opened(context)
        assertEquals(emptyList<WorkInfo.State>(), scheduled)

        for (refused in listOf<(Account) -> Unit>(Account::reject, Account::markUnreadable)) {
            signIn()
            refused(Services.account(context))
            Services.opened(context)
            assertEquals(emptyList<WorkInfo.State>(), scheduled)
        }

        signIn()
        Services.opened(context)
        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.ENQUEUED), scheduled)
    }

    @Test
    fun `a sign-in schedules the fetches, a failed one does not`() {
        home.token = { FakeHomeAssistant.json("""{"error":"invalid_grant"}""", 400) }
        assertFalse(Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1")))
        assertEquals(emptyList<WorkInfo.State>(), scheduled)

        home.token = { FakeHomeAssistant.json("""{"refresh_token":"refresh-1","token_type":"Bearer"}""") }
        assertTrue(Services.signIn(context, SignInRequest.start(home.address), AuthorizationCode("code-1")))
        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.ENQUEUED), scheduled)
    }

    @Test
    fun `fetches scheduled while a sign-out waits are cancelled after it`() {
        signIn()
        // The screen opening while the sign-out asks Home Assistant to end the token schedules the fetches anew.
        home.revoke = {
            FetchWork.start(context)
            MockResponse.Builder().code(200).build()
        }

        Services.signOut(context)

        assertTrue(scheduled.toString(), scheduled.isNotEmpty() && scheduled.all { it == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `signing out cancels both fetches`() {
        signIn()
        FetchWork.start(context)

        Services.signOut(context)

        assertTrue(scheduled.toString(), scheduled.isNotEmpty() && scheduled.all { it == WorkInfo.State.CANCELLED })
    }
}
