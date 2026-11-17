package dev.cupr.calendar.ha

import dev.cupr.calendar.model.CalendarVariables
import dev.cupr.calendar.model.DashboardColor
import dev.cupr.calendar.model.Entry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * One entry of `sensor.family_calendar`'s attribute `entries` as Home
 * Assistant delivers it (macro `calendar_entries` in homelab's
 * `zuhause.jinja`). Fields the app does not read, such as `recurrence_id`,
 * are ignored; `resolved_color` is read apart from these, since a value the
 * app cannot read leaves the entry in (EntryParser).
 */
@Serializable
private data class WireEntry(
    val start: String,
    val end: String,
    @SerialName("all_day") val allDay: Boolean,
    val title: String,
    val calendar: String,
    val kind: String? = null,
    val icon: String? = null,
    val color: String? = null,
    val categories: List<String>? = null,
    val year: Int? = null,
    val drawing: String? = null,
    val uid: String? = null,
)

/**
 * One entry that could be read: the [entry] and its [json] to keep in the
 * snapshot, as Home Assistant sent it but without a birth year or a
 * dashboard's colour that was dropped.
 */
class ReadEntry(
    val entry: Entry,
    val json: JsonObject,
)

/**
 * The entries read from Home Assistant's list: [read] the ones that could be
 * read, [problems] one line for each entry left out or changed, saying which
 * and why.
 */
class ParsedEntries(
    val read: List<ReadEntry>,
    val problems: List<String>,
) {
    val entries: List<Entry>
        get() = read.map { it.entry }
}

/**
 * Reads Home Assistant's entries one by one: an entry that cannot be read is
 * left out and named in [ParsedEntries.problems], and the others are used.
 * A birth year after the year the entry starts in is dropped, so no age is
 * shown for it, and named too.
 *
 * An entry's `resolved_color`, the colour the dashboard shows it in (D49),
 * is `#rrggbb`, `#rgb` or a CSS colour name, a fixed colour; or
 * `var(--zuhause-calendar-…)` naming one of [CalendarVariables.all], that
 * theme variable; or `var(--secondary-text-color)`. Any other value is
 * dropped and named, and the entry kept with its own colour and its
 * calendar's; unlike an unknown `color`, which leaves the entry out.
 *
 * An all-day entry's start and end are dates, its end the day after its last
 * day; a timed entry's are ISO date-times with an offset, read as wall-clock
 * times in [zone].
 */
object EntryParser {
    private val json = Json { ignoreUnknownKeys = true }

    private const val RESOLVED_COLOR = "resolved_color"
    private const val SECONDARY_TEXT = "secondary-text-color"

    fun parse(
        array: JsonArray,
        zone: ZoneId,
    ): ParsedEntries {
        val read = mutableListOf<ReadEntry>()
        val problems = mutableListOf<String>()
        array.forEachIndexed { index, element ->
            val name = describe(index, element)
            try {
                read += read(element, zone, name, problems)
            } catch (e: IllegalArgumentException) {
                // Not an object, a field missing or of the wrong type (SerializationException), an unknown colour,
                // or Entry's own checks: an entry that ends before it starts, an all-day one off midnight or
                // covering no day.
                problems += "$name left out: ${firstLine(e)}"
            } catch (e: DateTimeException) {
                problems += "$name left out: ${firstLine(e)}"
            }
        }
        return ParsedEntries(read, problems)
    }

    private fun read(
        element: JsonElement,
        zone: ZoneId,
        name: String,
        problems: MutableList<String>,
    ): ReadEntry {
        require(element is JsonObject) { "not an object" }
        val wire = json.decodeFromJsonElement(WireEntry.serializer(), element)
        val start = time(wire.start, wire.allDay, zone)
        val end = time(wire.end, wire.allDay, zone)
        val color = wire.color?.let { requireNotNull(CssColors.parse(it)) { "unknown colour \"$it\"" } }
        var year = wire.year
        var kept: JsonObject = element
        if (year != null && year > start.year) {
            problems += "$name: birth year $year is after the entry's year ${start.year}, so no age is shown"
            year = null
            kept = JsonObject(kept - "year")
        }
        val sent = element[RESOLVED_COLOR]?.takeUnless { it is JsonNull }
        val dashboardColor = sent?.let(::dashboardColor)
        if (sent != null && dashboardColor == null) {
            problems += "$name: resolved_color $sent is no colour the app reads, so its own or its calendar's applies"
            kept = JsonObject(kept - RESOLVED_COLOR)
        }
        val entry =
            Entry(
                start = start,
                end = end,
                allDay = wire.allDay,
                title = wire.title,
                calendar = wire.calendar,
                kind = wire.kind,
                color = color,
                dashboardColor = dashboardColor,
                icon = wire.icon,
                drawing = wire.drawing,
                categories = wire.categories.orEmpty(),
                year = year,
                uid = wire.uid,
            )
        return ReadEntry(entry, kept)
    }

    /** A string of one of the forms of `resolved_color`, else none. */
    private fun dashboardColor(sent: JsonElement): DashboardColor? {
        val text = (sent as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val variable = text.removeSurrounding("var(--", ")").takeIf { it != text }
        return when (variable) {
            null -> CssColors.parse(text)?.let { DashboardColor.Fixed(it) }
            SECONDARY_TEXT -> DashboardColor.SecondaryText
            in CalendarVariables.all -> DashboardColor.ThemeVariable(variable)
            else -> null
        }
    }

    private fun time(
        text: String,
        allDay: Boolean,
        zone: ZoneId,
    ): LocalDateTime =
        if (allDay) {
            LocalDate.parse(text).atStartOfDay()
        } else {
            OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDateTime()
        }

    /** The decoder's messages go on with the JSON input on further lines; the entry is named already. */
    private fun firstLine(e: Exception): String =
        e.message
            .orEmpty()
            .lineSequence()
            .first()

    /** "Entry 3 (uid …, "title")", with what the element has of the two. */
    private fun describe(
        index: Int,
        element: JsonElement,
    ): String {
        val fields = element as? JsonObject
        val uid = fields?.string("uid")
        val title = fields?.string("title")
        val parts = listOfNotNull(uid?.let { "uid $it" }, title?.let { "\"$it\"" })
        return if (parts.isEmpty()) "Entry $index" else "Entry $index (${parts.joinToString(", ")})"
    }
}
