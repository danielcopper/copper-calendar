package dev.cupr.calendar.store

import android.util.Log
import androidx.core.util.AtomicFile
import dev.cupr.calendar.ha.EntryParser
import dev.cupr.calendar.model.Entry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * Home Assistant's last good answer (D6, D45): [entryJson], the entries the
 * app could read, as Home Assistant sent them; the theme "Zuhause" (none
 * when Home Assistant had no theme of that name, D17); the signed-in user's
 * name; and when it was fetched. Replaced whole by each successful fetch,
 * deleted by each new sign-in and by signing out.
 */
@Serializable
data class Snapshot(
    val fetchedAtMillis: Long,
    val user: String,
    @SerialName("entries") val entryJson: List<JsonObject>,
    val theme: JsonObject? = null,
) {
    val fetchedAt: Instant
        get() = Instant.ofEpochMilli(fetchedAtMillis)

    /** The entries, read again as at the fetch; the ones kept all could be read then. */
    fun entries(zone: ZoneId): List<Entry> = EntryParser.parse(JsonArray(entryJson), zone).entries
}

/**
 * The snapshot in one JSON file, written whole through [AtomicFile], so a
 * reader finds the old snapshot or the new one, never a part.
 */
class SnapshotStore internal constructor(
    path: File,
    atomic: (File) -> AtomicFile,
) {
    constructor(path: File) : this(path, ::AtomicFile)

    private val file = atomic(path)

    /** The snapshot, or none when there is none yet or the file cannot be read (logged). */
    fun read(): Snapshot? =
        try {
            json.decodeFromString(Snapshot.serializer(), String(file.readFully(), Charsets.UTF_8))
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            Log.w(TAG, "The snapshot cannot be read", e)
            null
        } catch (e: IllegalArgumentException) {
            // SerializationException is one: the file holds no snapshot of this form.
            Log.w(TAG, "The snapshot is not a snapshot: ${e.javaClass.simpleName}")
            null
        }

    /**
     * Throws [IOException] when the new file cannot be written, the old
     * snapshot staying then; or when the file read back after the write does
     * not hold exactly the bytes written, since AtomicFile's `finishWrite`
     * only logs a failed sync, close or rename (androidx.core 1.19.1), which
     * would leave the old snapshot, of any length, in place.
     */
    fun write(snapshot: Snapshot) {
        val bytes = json.encodeToString(Snapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        var written = false
        try {
            out.write(bytes)
            file.finishWrite(out)
            written = true
        } finally {
            // Whatever stopped the write, its new file goes and the old snapshot stays.
            if (!written) file.failWrite(out)
        }
        if (!file.readFully().contentEquals(bytes)) throw IOException("the snapshot was not written")
    }

    fun clear() = file.delete()

    private companion object {
        const val TAG = "Snapshot"
        val json = Json { ignoreUnknownKeys = true }
    }
}
