package com.fitifiti.tv.data.tmdb

import com.fitifiti.tv.data.remote.REMOTE_BASE
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

/** Yazısız yatay sahne görseli + logolu başlık (sitedeki /api/art). Kullanıcı kendi TMDB anahtarını girdiyse doğrudan TMDB,
 *  girmediyse ozul.com.tr/api/tv-tmdb aracısı (sitenin anahtarı sunucuda kalır). */
data class Art(val backdrop: String? = null, val logo: String? = null, val poster: String? = null, val overview: String? = null, val vote: Double = 0.0, val votes: Int = 0, val tmdbId: Int? = null)

/** IMDb / Rotten Tomatoes / Metacritic + ödül özeti (OMDb; sitenin /api/reviews'i üzerinden) */
data class Critics(val imdb: Double? = null, val imdbVotes: Int? = null, val rt: Int? = null, val mc: Int? = null, val awards: String? = null,
                   val imdbId: String? = null, val major: List<AwardCount> = emptyList())
/** Büyük tören başına ödül/adaylık (Oscar, Emmy, Altın Küre, BAFTA, Cannes…) */
data class AwardCount(val family: String, val wins: Int, val noms: Int)

data class CastMember(val name: String, val role: String?, val photo: String?)
data class EpisodeArt(val name: String?, val overview: String?, val still: String?, val runtime: Int?)

class ArtRepository(private val http: OkHttpClient, private val key: () -> String) {
    private val cache = ConcurrentHashMap<String, Art>()

    private fun clean(s: String) = s.replace(Regex("\\[[^\\]]*\\]|\\([^)]*\\)"), " ").replace(Regex("\\s+-\\s+.+$"), "").replace(Regex("\\s+"), " ").trim()

    fun cached(kind: String, title: String, year: String?, tmdbId: String?) = cache[cacheKey(kind, title, year, tmdbId)]

    /** Ekran önizleme testleri: ağa çıkılmaz, önbellek elle doldurulur */
    @Volatile var offline = false
    fun seed(kind: String, title: String, year: String?, tmdbId: String?, art: Art) { cache[cacheKey(kind, title, year, tmdbId)] = art }
    fun seedCast(kind: String, tmdbId: Int, list: List<CastMember>) { castCache["$kind-$tmdbId"] = list }
    fun seedSeason(tmdbId: Int, season: Int, map: Map<Int, EpisodeArt>) { epCache["$tmdbId-$season"] = map }
    fun seedCritics(kind: String, tmdbId: Int, c: Critics) { criticsCache["$kind-$tmdbId"] = c }
    private fun cacheKey(kind: String, title: String, year: String?, tmdbId: String?) = "$kind|${tmdbId ?: ""}|${clean(title).lowercase()}|${year ?: ""}"

    suspend fun art(kind: String, title: String, year: String?, tmdbId: String? = null): Art {
        val k = key().trim()
        val ck = cacheKey(kind, title, year, tmdbId)
        cache[ck]?.let { return it }
        if (offline) return Art()
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
            }.getOrNull()
        } ?: return Art()
        cache[ck] = result
        return result
    }

    private fun get(key: String, path: String, params: Map<String, String>): JsonObject? {
        if (offline) return null
        val url = if (key.isNotEmpty()) "https://api.themoviedb.org/3$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("api_key", key); params.forEach { (a, b) -> addQueryParameter(a, b) }
        }.build() else "$REMOTE_BASE/api/tv-tmdb".toHttpUrl().newBuilder().apply {
            addQueryParameter("path", path); params.forEach { (a, b) -> addQueryParameter(a, b) }
        }.build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            // Aracı sınırı (429) / sunucu hatası: önbelleğe "yok" yazılmasın, sonra tekrar denensin
            if (r.code == 429 || r.code >= 500) throw java.io.IOException("HTTP ${r.code}")
            if (!r.isSuccessful) return null
            return AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject
        }
    }

    private val castCache = ConcurrentHashMap<String, List<CastMember>>()
    private val epCache = ConcurrentHashMap<String, Map<Int, EpisodeArt>>()

    /** Oyuncu kadrosu (dizide tüm sezonların toplamı: aggregate_credits) */
    suspend fun cast(kind: String, tmdbId: Int): List<CastMember> {
        val k = key().trim()
        val ck = "$kind-$tmdbId"
        castCache[ck]?.let { return it }
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val path = if (kind == "series") "/tv/$tmdbId/aggregate_credits" else "/movie/$tmdbId/credits"
                (get(k, path, mapOf("language" to "tr-TR"))?.get("cast") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.take(16).map { o ->
                    val role = o.str("character") ?: ((o["roles"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("character")
                    CastMember(o.str("name") ?: "", role, o.str("profile_path")?.let { "https://image.tmdb.org/t/p/w185$it" })
                }.filter { it.name.isNotBlank() }
            }.getOrNull()
        } ?: return emptyList()
        castCache[ck] = list
        return list
    }

    /** Sezonun bölüm görselleri/özetleri (tr → en) */
    suspend fun season(tmdbId: Int, season: Int): Map<Int, EpisodeArt> {
        val k = key().trim()
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
            }.getOrNull()
        } ?: return emptyMap()
        epCache[ck] = map
        return map
    }

    private val criticsCache = ConcurrentHashMap<String, Critics>()

    /** Eleştirmen puanları: kullanıcının anahtarından bağımsız, hep sitenin sunucusundan (OMDb anahtarı orada) */
    suspend fun critics(kind: String, tmdbId: Int): Critics? {
        val ck = "$kind-$tmdbId"
        criticsCache[ck]?.let { return it }
        if (offline) return null
        val c = withContext(Dispatchers.IO) {
            runCatching {
                val url = "$REMOTE_BASE/api/reviews".toHttpUrl().newBuilder()
                    .addQueryParameter("kind", if (kind == "series") "series" else "movie").addQueryParameter("tmdbId", "$tmdbId").build()
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    val o = (AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject)?.get("critics") as? JsonObject ?: return@runCatching Critics()
                    val major = (o["major"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { m ->
                        AwardCount(m.str("family") ?: return@mapNotNull null, m.int("wins") ?: 0, m.int("noms") ?: 0)
                    }
                    Critics(o.dbl("imdb"), o.dbl("imdbVotes")?.toInt(), o.dbl("rt")?.toInt(), o.dbl("mc")?.toInt(), o.str("awards"), o.str("imdbId"), major)
                }
            }.getOrNull()
        } ?: return null
        criticsCache[ck] = c
        return c
    }

    private val awardsCache = ConcurrentHashMap<String, List<AwardCount>>()

    /** Wikidata'dan tören başına ödüller (sitenin /api/awards'ı; OMDb özeti yalnız en önemli töreni söylüyor) */
    suspend fun awards(imdbId: String): List<AwardCount> {
        awardsCache[imdbId]?.let { return it }
        if (offline) return emptyList()
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val url = "$REMOTE_BASE/api/awards".toHttpUrl().newBuilder().addQueryParameter("imdbId", imdbId).build()
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    ((AppJson.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject)?.get("groups") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                        .mapNotNull { g -> AwardCount(g.str("name") ?: return@mapNotNull null, g.int("wins") ?: 0, g.int("noms") ?: 0) }
                }
            }.getOrNull()
        } ?: return emptyList()
        awardsCache[imdbId] = list
        return list
    }
    fun seedAwards(imdbId: String, list: List<AwardCount>) { awardsCache[imdbId] = list }

    /** Anahtar doğru mu? (Ayarlar'da) */
    suspend fun validate(k: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { get(k.trim(), "/configuration", emptyMap()) != null }.getOrDefault(false)
    }
}
