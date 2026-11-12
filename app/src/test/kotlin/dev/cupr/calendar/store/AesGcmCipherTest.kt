package dev.cupr.calendar.store

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.security.KeyStoreException
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The cipher with a software AES key; on the phone the key is the Android Keystore's, which Robolectric lacks. */
class AesGcmCipherTest {
    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = key()
    private val cipher = AesGcmCipher { key }
    private val token = "refresh-1".toByteArray()

    @Test
    fun `a sealed token opens again to the same bytes`() {
        val sealed = cipher.seal(token)

        assertArrayEquals(token, cipher.open(sealed))
        assertEquals(12 + token.size + 16, sealed.size)
        assertFalse(sealed.toList().windowed(token.size).any { it == token.toList() })
    }

    @Test
    fun `each sealing has its own IV`() {
        assertFalse(cipher.seal(token).contentEquals(cipher.seal(token)))
    }

    @Test
    fun `a changed, foreign or short sealed token is unreadable`() {
        val sealed = cipher.seal(token)
        val changed = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        val other = key()

        assertThrows(UnreadableTokenException::class.java) { cipher.open(changed) }
        assertThrows(UnreadableTokenException::class.java) { AesGcmCipher { other }.open(sealed) }
        assertThrows(UnreadableTokenException::class.java) { cipher.open(ByteArray(12)) }
        // Shorter than an IV and a tag: no sealing of even an empty token is that short. Checked before the
        // Keystore is asked, which might else throw IllegalBlockSizeException, a cipher failure.
        val keystoreDown = AesGcmCipher { throw ProviderException("Keystore") }
        assertThrows(UnreadableTokenException::class.java) { keystoreDown.open(ByteArray(27)) }
    }

    @Test
    fun `a Keystore that fails is a cipher failure, on sealing and on opening`() {
        val sealed = cipher.seal(token)
        for (failure in listOf(ProviderException("Keystore"), KeyStoreException("Keystore"))) {
            val failing = AesGcmCipher { throw failure }

            val onSeal = assertThrows(CipherFailureException::class.java) { failing.seal(token) }
            val onOpen = assertThrows(CipherFailureException::class.java) { failing.open(sealed) }

            assertEquals(failure.javaClass.simpleName, onSeal.message)
            assertEquals(failure.javaClass.simpleName, onOpen.message)
        }
    }

    @Test
    fun `a Keystore that cannot be read is a cipher failure`() {
        // KeyStore.load reads from the Keystore service and declares IOException.
        val unreadable = AesGcmCipher { throw IOException("Keystore") }

        assertThrows(CipherFailureException::class.java) { unreadable.seal(token) }
    }
}
