package dev.cupr.calendar.ha

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** A field that is a JSON string, or none. */
internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A field that is a JSON object, or none. */
internal fun JsonObject.objectField(name: String): JsonObject? = this[name]?.takeIf { it is JsonObject }?.jsonObject

/** Text that is a JSON object, or none. */
internal fun parseObject(text: String): JsonObject? =
    try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    }
