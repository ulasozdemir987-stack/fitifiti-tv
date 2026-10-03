package com.fitifiti.tv.data.xtream

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Büyük listeler (binlerce film / on binlerce kanal) için akış okuyucu: yanıt belleğe tek parça alınmaz,
 * dizideki her nesne okunurken yalnız düz alanları (dizi alanlarında ilk metin) tutulur. TV'lerin küçük
 * belleğinde tüm JSON ağacını kurmak dakikalarca sürüyor ya da uygulamayı düşürüyordu.
 */
class Row(private val m: Map<String, String>) {
    fun str(key: String): String? = m[key]?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    fun int(key: String): Int? = str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.toInt() }
    fun long(key: String): Long? = str(key)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    fun dbl(key: String): Double? = str(key)?.replace(',', '.')?.toDoubleOrNull()
    fun firstStr(key: String): String? = str(key)
}

/** Kök bir dizi ise her nesne için [map] çağrılır; dizi değilse (hata nesnesi vb.) boş liste. */
fun <T> readRows(input: InputStream, map: (Row) -> T?): List<T> {
    val out = ArrayList<T>()
    JsonReader(InputStreamReader(input, Charsets.UTF_8).buffered(64 * 1024)).use { r ->
        r.isLenient = true
        when (r.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    map(Row(readFlatObject(r)))?.let(out::add)
                }
                r.endArray()
            }
            // Bazı paneller listeyi { "1": {...}, "2": {...} } biçiminde verir
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject()
                while (r.hasNext()) {
                    r.nextName()
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    map(Row(readFlatObject(r)))?.let(out::add)
                }
                r.endObject()
            }
            else -> {}
        }
    }
    return out
}

private fun readFlatObject(r: JsonReader): Map<String, String> {
    val m = HashMap<String, String>(32)
    r.beginObject()
    while (r.hasNext()) {
        val k = r.nextName()
        when (r.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> m[k] = r.nextString()
            JsonToken.BOOLEAN -> m[k] = r.nextBoolean().toString()
            JsonToken.NULL -> r.nextNull()
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray()
                var first: String? = null
                while (r.hasNext()) {
                    if (first == null && (r.peek() == JsonToken.STRING || r.peek() == JsonToken.NUMBER)) first = r.nextString().takeIf { it.isNotBlank() }
                    else r.skipValue()
                }
                r.endArray()
                if (first != null) m[k] = first
            }
            else -> r.skipValue()
        }
    }
    r.endObject()
    return m
}
