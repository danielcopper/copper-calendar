package dev.cupr.calendar.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The copy of copper-design's Android port in `src/main` is the port at the
 * pinned release, byte for byte (D65): copied unchanged, never edited here.
 * Moving the pin means copying the port's files at the new tag and writing
 * their SHA-256 and the tag below.
 */
class CopperPortTest {
    @Test
    fun `each copied file is the pinned release's file, byte for byte`() {
        for ((path, sha256) in FILES) {
            assertEquals("$path is not copper-design $TAG's file ($COMMIT)", sha256, sha256(File(MAIN, path)))
        }
    }

    @Test
    fun `the port's Kotlin package holds only the copied files`() {
        val copied = FILES.keys.filter { it.startsWith(KOTLIN) }.mapTo(mutableSetOf()) { it.substringAfterLast('/') }
        val present = File(MAIN, KOTLIN).list().orEmpty().toSet()
        assertEquals(copied, present)
    }

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toHexString()

    private companion object {
        /** copper-design's release the copy is taken from, and its commit. */
        const val TAG = "v0.3.0"
        const val COMMIT = "8386fabaed1615b350874d5f010faba54cfde472"

        /** Where `ports/android/` is copied to. */
        const val MAIN = "src/main"
        const val KOTLIN = "kotlin/dev/cupr/design"

        /** Each file of `ports/android/` at [TAG], by its path below it, and its SHA-256. */
        val FILES =
            mapOf(
                "$KOTLIN/CopperColorScheme.kt" to "66f9cb5aa1a57b318432e5e0918a369e0bdcb898a3a082b33f3eb05fc9cc47f8",
                "$KOTLIN/CopperColors.kt" to "563687685602dbe1cda3a57deb184fa8b98cbdf41e0293053089748b5066c9ef",
                "res/values/copper_colors.xml" to "a64c6bc0311836c0f1832aa16e64c4f4d76f6d135e35e535facb7b5db26c1494",
                "res/values-night/copper_colors.xml" to
                    "1b3f93cebb70eb3aa1a3d8ab2c8b733849b6b7c69c6575dc30cceb34b99cf72b",
            )
    }
}
