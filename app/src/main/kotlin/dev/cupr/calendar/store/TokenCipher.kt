package dev.cupr.calendar.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals the refresh token before it is stored, and opens it again (D45). */
interface TokenCipher {
    /** Throws [CipherFailureException] when the Keystore or the cipher fails. */
    fun seal(plain: ByteArray): ByteArray

    /**
     * Throws [UnreadableTokenException] when [sealed] cannot be opened with
     * this cipher's key: sealed with another key, changed, or too short.
     * Throws [CipherFailureException] when the Keystore or the cipher fails,
     * which may pass.
     */
    fun open(sealed: ByteArray): ByteArray
}

/** The sealed token cannot be opened with this key; signing in again is the only way on (D45). */
class UnreadableTokenException(
    message: String,
) : Exception(message)

/** The Keystore or the cipher failed; trying again later may work (D45). */
class CipherFailureException(
    reason: String,
    cause: Throwable? = null,
) : Exception(reason, cause)

/**
 * AES-256-GCM with the key [key] gives: the sealed form is the 12-byte IV
 * the cipher chose followed by the ciphertext and its tag. The app's key
 * lives in the Android Keystore ([KeystoreKey], D45).
 */
class AesGcmCipher(
    private val key: () -> SecretKey,
) : TokenCipher {
    override fun seal(plain: ByteArray): ByteArray =
        guarded {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv: ByteArray? = cipher.iv
            if (iv?.size != IV_BYTES) throw CipherFailureException("the cipher chose an IV of ${iv?.size} bytes")
            iv + cipher.doFinal(plain)
        }

    override fun open(sealed: ByteArray): ByteArray {
        if (sealed.size < IV_BYTES + TAG_BITS / Byte.SIZE_BITS) throw UnreadableTokenException("too short to be sealed")
        return guarded {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }
    }

    /**
     * Runs [block] and turns what the Keystore and the cipher throw into this
     * interface's two exceptions: a tag that does not match into
     * [UnreadableTokenException], any other security, provider or I/O
     * failure (`KeyStore.load` reads from a service) into
     * [CipherFailureException]. Anything else passes unchanged.
     */
    private inline fun <T> guarded(block: () -> T): T =
        runCatching(block).getOrElse { e ->
            throw when (e) {
                is AEADBadTagException -> {
                    UnreadableTokenException("sealed with another key, or changed")
                }

                is GeneralSecurityException, is ProviderException, is IOException -> {
                    CipherFailureException(e.javaClass.simpleName, e)
                }

                else -> {
                    e
                }
            }
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * The app's AES key in the Android Keystore, made on first use. It never
 * leaves the Keystore, so the stored token cannot be read from the app's
 * files alone, not even from a debug build's with `run-as`.
 */
object KeystoreKey {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "refresh-token"
    private const val KEY_BITS = 256

    /**
     * Synchronized: a sign-out opens the stored token outside the fetch's
     * lock, so it and a fetch could otherwise each make a key.
     */
    @Synchronized
    fun get(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }
}
