package dev.cupr.calendar.store

import androidx.core.util.AtomicFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SnapshotStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "snapshot.json") }
    private val store by lazy { SnapshotStore(file) }

    private val entry =
        Json
            .parseToJsonElement(
                """{"start":"2026-11-11","end":"2026-11-12","all_day":true,""" +
                    """"title":"Martinstag","calendar":"special"}""",
            ).jsonObject
    private val theme = Json.parseToJsonElement("""{"primary-color":"#1f6fd6"}""").jsonObject
    private val snapshot = Snapshot(1_794_470_400_000, "Daniel", listOf(entry), theme)

    @Test
    fun `there is no snapshot before the first fetch`() {
        assertNull(store.read())
    }

    @Test
    fun `a snapshot reads back whole`() {
        store.write(snapshot)

        assertEquals(snapshot, store.read())
        assertEquals(Instant.ofEpochMilli(1_794_470_400_000), store.read()!!.fetchedAt)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 11).atStartOfDay()),
            store.read()!!.entries(ZoneId.of("Europe/Berlin")).map { it.start },
        )
    }

    @Test
    fun `a new snapshot replaces the old one whole`() {
        store.write(snapshot)
        val next = Snapshot(1_794_471_300_000, "Daniel", emptyList(), null)

        store.write(next)

        assertEquals(next, store.read())
    }

    @Test
    fun `the entries are stored under the key they had, entries`() {
        store.write(snapshot)

        val json = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(setOf("fetchedAtMillis", "user", "entries", "theme"), json.keys)
    }

    @Test
    fun `a write that cannot start keeps the old snapshot`() {
        store.write(snapshot)
        File(file.path + ".new", "blocking").mkdirs()

        assertThrows(IOException::class.java) { store.write(Snapshot(2, "Daniel", emptyList())) }

        assertEquals(snapshot, store.read())
    }

    @Test
    fun `a write whose file does not end up in place is a failure`() {
        // A directory where the snapshot belongs: AtomicFile's rename onto it fails, which it only logs.
        File(file, "blocking").mkdirs()

        assertThrows(IOException::class.java) { store.write(snapshot) }
    }

    @Test
    fun `a rename that fails over an old snapshot of the same length is a failure`() {
        store.write(Snapshot(1, "Daniel", emptyList()))
        val renameFails = SnapshotStore(file, ::RenameFails)

        // Same length: 1 and 2 are one digit each.
        assertThrows(IOException::class.java) { renameFails.write(Snapshot(2, "Daniel", emptyList())) }

        assertEquals(Snapshot(1, "Daniel", emptyList()), store.read())
    }

    @Test
    fun `a rename that fails over an old snapshot of another length is a failure`() {
        store.write(Snapshot(1, "Daniel", emptyList()))

        assertThrows(IOException::class.java) {
            SnapshotStore(file, ::RenameFails).write(Snapshot(10_000, "Daniel", emptyList()))
        }
    }

    /**
     * AtomicFile as androidx.core 1.19.1 leaves it after a rename that
     * fails: the new file closed, the old snapshot in place. The real one
     * logs the failure; this one does not.
     */
    private class RenameFails(
        file: File,
    ) : AtomicFile(file) {
        override fun finishWrite(str: FileOutputStream?) {
            str?.close()
        }
    }

    @Test
    fun `a file that holds no snapshot reads as none`() {
        file.writeText("""{"user":"Daniel"}""")
        assertNull(store.read())
        file.writeText("not json")
        assertNull(store.read())
    }

    @Test
    fun `clearing deletes the snapshot`() {
        store.write(snapshot)

        store.clear()

        assertNull(store.read())
        assertFalse(file.exists())
    }
}
