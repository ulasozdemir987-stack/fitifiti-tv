package com.fitifiti.tv.data.tmdb

import com.fitifiti.tv.data.xtream.AppJson
import com.fitifiti.tv.data.xtream.int
import com.fitifiti.tv.data.xtream.str
import com.fitifiti.tv.data.xtream.dbl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** Yazısız yatay sahne görseli + logolu başlık (sitedeki /api/art). TMDB anahtarı kullanıcının kendi anahtarıdır; yoksa boş döner. */
data class Art(val backdrop: String? = null, val logo: String? = null, val poster: String? = null, val overview: String? = null, val vote: Double = 0.0, val votes: Int = 0, val tmdbId: Int? = null)

data class CastMember(val name: String, val role: String?, val photo: String?)
data class EpisodeArt(val name: String?, val overview: String?, val still: String?, val runtime: Int?)

class ArtRepository(private val http: OkHttpClient, private val key: () -> String) {
    private val cache = ConcurrentHashMap<String, Art>()

    private fun clean(s: String) = s.replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)"), " ").replace(Regex("\\s+-\\s+.+$"), "").replace(Regex("\\s+"), " ").trim()

    fun cached(kind: String, title: String, year: String?, tmdbId: String?) = cache[cacheKey(kind, title, year, tmdbId)]
    private fun cacheKey(kind: String, title: String, year: String?, tmdbId: String?) = "$kind|${tmdbId ?: ""}|${clean(title).lowercase()}|${year ?: ""}"

    suspend fun art(kind: String, title: String, year: String?, tmdbId: String? = null): Art {
        val k = key().trim()
        if (k.isEmpty()) return Art()
        val ck = cacheKey(kind, title, year, tmdbId)
        cache[ck]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val type = if (kind == "series") "tv" else "movie"
                val id = tmdbId?.toIntOrNull()?.takeIf { type == "movie" } ?: run {
                    val p = mutableMapOf("query" to clean(title), "language" to "tr-TR")
                    if (!year.isNullOrBlank()) p[if (type == "tv") "first_air_date_year" else "year"] = year
                    (get(k, "/search/$type", p)?.get("results") as? JsonArray)?.firstOrNull()?.let { (it as JsonObject).int("id") }
                } ?: return@runCatching Art()
                val d = get(k, "/$type/$id", mapOf("language" to "tr-TR", "append_to_response" to "images", "include_image_language" to "tr,en,null")) ?: return@runCatching Art(tmdbId = id)
                val images = d["images"] as? JsonObject
                val backdrops = (images?.get("backdrops") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                // yazısız (dil yok) sahne görseli önce
                val bd = backdrops.firstOrNull { it.str("iso_639_1") == null }?.str("file_path") ?: d.str("backdrop_path")
                val logos = (images?.get("logos") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty().filter { it.str("file_path")?.endsWith(".svg") == false }
                val logo = (logos.firstOrNull { it.str("iso_639_1") == "tr" } ?: logos.firstOrNull { it.str("iso_639_1") == "en" } ?: logos.firstOrNull())?.str("file_path")
                Art(
                    backdrop = bd?.let { "https://image.tmdb.org/t/p/w1280$it" },
                    logo = logo?.let { "https://image.tmdb.org/t/p/w500$it" },
                    poster = d.str("poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
                    overview = d.str("overview"), vote = d.dbl("vote_average") ?: 0.0, votes = d.int("vote_count") ?: 0, tmdbId = id,
                )
            }.getOrDefault(Art())
        }
        cache[ck] = result
        return result
    }

    private fun get(key: String, path: String, params: Map<String, String>): JsonObject? {
        val url = "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", key); params.forEach { (a, b) -> addQueryParameter(a, b) }
        }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            return AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject
        }
    }

    private val castCache = ConcurrentHashMap<String, List<CastMember>>()
    private val epCache = ConcurrentHashMap<String, Map<Int, EpisodeArt>>()

    /** Oyuncu kadrosu (dizide tüm sezonların toplamı: aggregate_credits) */
    suspend fun cast(kind: String, tmdbId: Int): List<CastMember> {
        val k = key().trim(); if (k.isEmpty()) return emptyList()
        val ck = "$kind-$tmdbId"
        castCache[ck]?.let { return it }
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val path = if (kind == "series") "/tv/$tmdbId/aggregate_credits" else "/movie/$tmdbId/credits"
                (get(k, path, mapOf("language" to "tr-TR"))?.get("cast") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.take(16).map { o ->
                    val role = o.str("character") ?: ((o["roles"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("character")
                    CastMember(o.str("name") ?: "", role, o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" })
                }.filter { it.name.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        castCache[ck] = list
        return list
    }

    /** Sezonun bölüm görselleri/özetleri (tr → en) */
    suspend fun season(tmdbId: Int, season: Int): Map<Int, EpisodeArt> {
        val k = key().trim(); if (k.isEmpty()) return emptyMap()
        val ck = "$tmdbId-$season"
        epCache[ck]?.let { return it }
        val map = withContext(Dispatchers.IO) {
            runCatching {
                fun eps(lang: String) = (get(k, "/tv/$tmdbId/season/$season", mapOf("language" to lang))?.get("episodes") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                val tr = eps("tr-TR")
                val en = if (tr.any { it.str("overview").isNullOrBlank() }) eps("en-US").associateBy { it.int("episode_number") } else emptyMap()
                tr.mapNotNull { o ->
                    val n = o.int("episode_number") ?: return@mapNotNull null
                    n to EpisodeArt(
                        name = o.str("name")?.takeUnless { Regex("^(Bölüm|Episode) \\d+$").matches(it) } ?: en[n]?.str("name"),
                        overview = o.str("overview")?.takeIf { it.isNotBlank() } ?: en[n]?.str("overview"),
                        still = o.str("still_path")?.let { "https://image.tmdb.org/t/p/w500$it" },
                        runtime = o.int("runtime"),
                    )
                }.toMap()
            }.getOrDefault(emptyMap())
        }
        epCache[ck] = map
        return map
    }

    /** Anahtar doğru mu? (Ayarlar'da) */
    suspend fun validate(k: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { get(k.trim(), "/configuration", emptyMap()) != null }.getOrDefault(false)
    }
}
