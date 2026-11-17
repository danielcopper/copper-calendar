package dev.cupr.calendar.signin

import android.app.Application
import android.util.Log
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.cupr.calendar.R
import dev.cupr.calendar.app.Services
import dev.cupr.calendar.app.Settings
import dev.cupr.calendar.fetch.FetchWork
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.ha.RefreshToken
import dev.cupr.calendar.ha.SignInRequest
import dev.cupr.calendar.store.AesGcmCipher
import dev.cupr.calendar.store.FetchInterval
import dev.cupr.calendar.store.TokenCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator

/** The sign-in screen's "Abruf": how often the app fetches, shown and set while signed in. */
@RunWith(RobolectricTestRunner::class)
class SignInScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var keystoreCipher: () -> TokenCipher
    private lateinit var io: CoroutineDispatcher
    private val opened = mutableListOf<ActivityController<SignInActivity>>()

    @Before
    fun setUp() {
        keystoreCipher = Services.cipher
        Services.cipher = { AesGcmCipher { key } }
        // The status's read and the interval's change run at once, so the test sees them without waiting.
        io = Services.io
        Services.io = Dispatchers.Unconfined
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
        for (controller in opened) controller.pause().stop().destroy()
        Services.cipher = keystoreCipher
        Services.io = io
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `signed in, the screen shows the stored fetch interval, and picking another sets it`() {
        signIn()
        Settings.app(context).writeFetchInterval(FetchInterval.EVERY_30_MINUTES)

        open()

        node(R.string.fetch_interval).assertExists()
        node(R.string.every_30_minutes).assertIsSelected()
        node(R.string.every_15_minutes).assertIsNotSelected()
        node(R.string.every_60_minutes).assertIsNotSelected()
        assertEquals(TimeUnit.MINUTES.toMillis(30), periodicMillis())

        node(R.string.every_60_minutes).performClick()

        node(R.string.every_60_minutes).assertIsSelected()
        node(R.string.every_30_minutes).assertIsNotSelected()
        assertEquals(FetchInterval.EVERY_60_MINUTES, Settings.app(context).fetchInterval())
        assertEquals(TimeUnit.MINUTES.toMillis(60), periodicMillis())
    }

    @Test
    fun `signed out, the screen has no fetch interval`() {
        open()

        node(R.string.sign_in).assertExists()
        node(R.string.fetch_interval).assertDoesNotExist()
    }

    private fun signIn() =
        Services.account(context).signIn(
            checkNotNull(HaAddress.parse("ha.example.org")),
            SignInRequest.CLIENT_ID,
            RefreshToken("refresh-1"),
        )

    /** The periodic fetch's interval, as it is scheduled. */
    private fun periodicMillis(): Long? =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(FetchWork.PERIODIC)
            .get()
            .single()
            .periodicityInfo
            ?.repeatIntervalMillis

    private fun open() {
        opened += Robolectric.buildActivity(SignInActivity::class.java).setup()
        compose.waitForIdle()
    }

    private fun node(text: Int) = compose.onNodeWithText(context.getString(text))
}
