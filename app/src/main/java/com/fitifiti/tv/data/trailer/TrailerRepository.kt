package com.fitifiti.tv.data.trailer

import com.fitifiti.tv.data.remote.REMOTE_BASE
import com.fitifiti.tv.data.xtream.AppJson
import com.fitifiti.tv.data.xtream.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Fragmanlar sitenin sunucusundan: `/api/trailer` YouTube kimliğini bulur (sağlayıcının youtube_trailer alanı → arama),
 * `/api/trailer-file?id=` VPS'te indirilmiş 1080p H.264/AAC MP4'ü verir (yoksa indirmeye başlar, 202 döner).
 * TV'de YouTube oynatıcı yok; yalnız MP4 hazırsa oynatılır.
 */
class TrailerRepository(private val http: OkHttpClient) {
    private val ids = ConcurrentHashMap<String, String>() // "" = fragman yok
    private val ready = ConcurrentHashMap.newKeySet<String>()

    suspend fun find(kind: String, title: String, year: String?, provider: String?): String? {
        val key = "$kind|$title|${year ?: ""}"
        ids[key]?.let { return it.ifEmpty { null } }
        val id = withContext(Dispatchers.IO) {
            runCatching {
                val url = "$REMOTE_BASE/api/trailer".toHttpUrl().newBuilder().apply {
                    addQueryParameter("kind", if (kind == "series") "series" else "movie")
                    addQueryParameter("title", title.take(140))
                    if (!year.isNullOrBlank() && year.length == 4) addQueryParameter("year", year)
                    // sağlayıcı bazen tam adres, bazen yalnız kimlik verir; site ikisini de anlar
                    if (!provider.isNullOrBlank()) addQueryParameter("yt", provider.take(200))
                }.build()
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    ((AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject)?.str("id")).orEmpty()
                }
            }.getOrNull()
        } ?: return null // ağ hatası: önbelleğe yazma
        ids[key] = id
        return id.ifEmpty { null }
    }

    /** MP4 hazır mı? Hazır değilse sunucu bu soruyla indirmeye başlar. */
    suspend fun isReady(id: String): Boolean {
        if (id in ready) return true
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(fileUrl(id) + "&check=1").build()).execute().use { r ->
                    (AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject)?.get("ready")?.jsonPrimitive?.booleanOrNull == true
                }
            }.getOrDefault(false)
        }
        if (ok) ready.add(id)
        return ok
    }

    fun fileUrl(id: String) = "$REMOTE_BASE/api/trailer-file?id=" + java.net.URLEncoder.encode(id, "UTF-8")
}
