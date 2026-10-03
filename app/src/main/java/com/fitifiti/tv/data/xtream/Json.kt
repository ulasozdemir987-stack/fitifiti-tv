package com.fitifiti.tv.data.xtream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Xtream yanıtları tutarsız: sayılar bazen string, alanlar bazen null / "" / [] — hepsi gevşek okunur.
val AppJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

fun JsonObject.str(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    val s = p.contentOrNull?.trim() ?: return null
    return s.takeIf { it.isNotEmpty() && it != "null" }
}
fun JsonObject.int(key: String): Int? = str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
fun JsonObject.long(key: String): Long? = str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
fun JsonObject.dbl(key: String): Double? = str(key)?.replace(',', '.')?.toDoubleOrNull()
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
/** "backdrop_path" bazen dizi, bazen tek string */
fun JsonObject.firstStr(key: String): String? = when (val v = this[key]) {
    is JsonArray -> v.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
    is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    else -> null
}
fun JsonElement.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
