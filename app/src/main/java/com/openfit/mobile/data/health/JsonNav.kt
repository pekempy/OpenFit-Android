package com.openfit.mobile.data.health

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Null-safe JSON tree navigation for Google Health API v4's deeply nested,
 * loosely-typed responses - the Kotlin equivalent of the reference
 * implementation's liberal use of `?.` optional chaining over a dynamic
 * object. Every accessor returns null instead of throwing on a missing or
 * mistyped field, since a partial/evolving upstream schema shouldn't crash
 * a sync. */

fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
fun JsonElement?.arr(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

fun JsonElement?.str(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

fun JsonElement?.num(key: String): Double? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.doubleOrNull

fun JsonElement?.numInt(key: String): Int? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.intOrNull

fun JsonElement?.bool(key: String): Boolean? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

/** civil date/time objects look like {"date":{"year":2026,"month":9,"day":30}}
 * or {"time":{"hours":7,"minutes":30}} at the top level, or the date/time
 * object may itself BE what's passed in (both shapes appear across
 * endpoints, matching the reference implementation's dateFromCivil()). */
fun dateFromCivil(value: JsonElement?): String? {
    val date = value.obj("date") ?: value.asObj()
    val year = date.numInt("year") ?: return null
    val month = date.numInt("month") ?: return null
    val day = date.numInt("day") ?: return null
    return "%04d-%02d-%02d".format(year, month, day)
}

fun timeFromCivil(value: JsonElement?): String? {
    val time = value.obj("time") ?: value.asObj()
    val hours = time.numInt("hours") ?: return null
    val minutes = time.numInt("minutes") ?: 0
    return "%02d:%02d".format(hours, minutes)
}

/** Parses a protobuf Duration-style string like "1234.5s" -> seconds. */
fun durationSeconds(value: String?): Double {
    if (value.isNullOrBlank()) return 0.0
    return value.removeSuffix("s").toDoubleOrNull() ?: 0.0
}

fun JsonElement?.rollupPoints(): List<JsonObject> =
    this.arr("rollupDataPoints")?.filterIsInstance<JsonObject>() ?: emptyList()

fun JsonElement?.dataPoints(): List<JsonObject> =
    this.arr("dataPoints")?.filterIsInstance<JsonObject>() ?: emptyList()

/** Builds date -> value from a dailyRollUp response, keyed by each point's
 * civilStartTime. */
fun <T> dailyRollupMap(payload: JsonElement?, extract: (JsonObject) -> T?): Map<String, T> {
    val result = LinkedHashMap<String, T>()
    for (point in payload.rollupPoints()) {
        val date = dateFromCivil(point["civilStartTime"]) ?: continue
        val value = extract(point) ?: continue
        result[date] = value
    }
    return result
}

/** Builds date -> value from a dataPoints:reconcile response for a "daily"
 * record type, keyed by record.date. */
fun <T> dailyRecordMap(payload: JsonElement?, recordKey: String, extract: (JsonObject) -> T?): Map<String, T> {
    val result = LinkedHashMap<String, T>()
    for (point in payload.dataPoints()) {
        val record = point.obj(recordKey) ?: continue
        val date = dateFromCivil(record["date"]) ?: continue
        val value = extract(record) ?: continue
        result[date] = value
    }
    return result
}
