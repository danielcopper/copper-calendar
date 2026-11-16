package dev.cupr.calendar.store

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import dev.cupr.calendar.ha.HaAddress
import dev.cupr.calendar.ha.RefreshToken
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
class AccountTest {
    private val prefs =
        ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("account", Context.MODE_PRIVATE)
    private val key = key()
    private val account = Account(prefs, AesGcmCipher { key })
    private val address = HaAddress("https://home.example/".toHttpUrl())
    private val token = RefreshToken("refresh-1")

    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `nothing is signed in at first`() {
        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(Account.Opened.NotSignedIn, account.open())
        assertNull(account.address)
    }

    @Test
    fun `a sign-in keeps the address, the exact client id and the refresh token, sealed`() {
        account.signIn(address, "http://127.0.0.1:47123/", token)

        assertEquals(Account.State.SIGNED_IN, account.state)
        val session = (account.open() as Account.Opened.Usable).session
        assertEquals(address, session.address)
        assertEquals("http://127.0.0.1:47123/", session.clientId)
        assertEquals(token, session.refreshToken)
        assertFalse(prefs.all.values.any { it.toString().contains("refresh-1") })
        assertEquals("Session(…)", session.toString())
    }

    @Test
    fun `a token that cannot be sealed keeps nothing`() {
        val failing = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") })

        assertThrows(CipherFailureException::class.java) { failing.signIn(address, "client", token) }

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(emptySet<String>(), prefs.all.keys)
    }

    @Test
    fun `a refused sign-in forgets the token and keeps the address`() {
        account.signIn(address, "client", token)

        account.reject()

        assertEquals(Account.State.REJECTED, account.state)
        assertEquals(Account.Opened.NotSignedIn, account.open())
        assertEquals(address, account.address)
        assertFalse(prefs.contains("refresh_token"))
    }

    @Test
    fun `an unreadable sign-in is kept apart from a refused one`() {
        account.signIn(address, "client", token)

        account.markUnreadable()

        assertEquals(Account.State.UNREADABLE, account.state)
        assertFalse(prefs.contains("refresh_token"))
        assertEquals(address, account.address)
    }

    @Test
    fun `signing out forgets the token and keeps the address`() {
        account.signIn(address, "client", token)

        account.signOut()

        assertEquals(Account.State.SIGNED_OUT, account.state)
        assertEquals(Account.Opened.NotSignedIn, account.open())
        assertEquals(address, account.address)
        assertEquals(setOf("address"), prefs.all.keys)
    }

    @Test
    fun `a token sealed with a key that is gone is unreadable`() {
        account.signIn(address, "client", token)
        val other = key()

        val opened = Account(prefs, AesGcmCipher { other }).open()

        assertEquals(Account.Opened.Unreadable("the stored token: sealed with another key, or changed"), opened)
    }

    @Test
    fun `a stored token that is not Base64, or a sign-in with a field missing, is unreadable`() {
        account.signIn(address, "client", token)
        prefs.edit { putString("refresh_token", "not base64 !") }
        assertEquals(Account.Opened.Unreadable("the stored token is not Base64"), account.open())

        prefs.edit { remove("client_id") }
        assertEquals(Account.Opened.Unreadable("the stored sign-in is incomplete"), account.open())
    }

    @Test
    fun `an unexpected exception from the cipher is not taken for a stored text that is not Base64`() {
        account.signIn(address, "client", token)
        val odd =
            object : TokenCipher {
                override fun seal(plain: ByteArray) = plain

                override fun open(sealed: ByteArray): ByteArray = throw IllegalArgumentException("odd")
            }

        assertThrows(IllegalArgumentException::class.java) { Account(prefs, odd).open() }
    }

    @Test
    fun `a Keystore that fails leaves the sign-in to open next time`() {
        account.signIn(address, "client", token)

        val opened = Account(prefs, AesGcmCipher { throw ProviderException("Keystore") }).open()

        assertEquals(Account.Opened.Unavailable("the Keystore failed: ProviderException"), opened)
        assertEquals(Account.State.SIGNED_IN, account.state)
        assertEquals(token, (account.open() as Account.Opened.Usable).session.refreshToken)
    }

    @Test
    fun `an address remembered before signing in is kept`() {
        account.rememberAddress(address)

        assertEquals(address, account.address)
        assertEquals(Account.State.SIGNED_OUT, account.state)
    }
}
