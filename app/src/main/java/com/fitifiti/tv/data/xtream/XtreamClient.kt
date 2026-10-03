package com.fitifiti.tv.data.xtream

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Bazı sağlayıcılar tarayıcı olmayan istemciyi reddediyor */
const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

class XtreamException(message: String) : IOException(message)

class XtreamClient(private val http: OkHttpClient, val account: Account) {

    val base: String = normalizeServer(account.server)

    private suspend fun call(action: String?, vararg params: Pair<String, String>): JsonElement = withContext(Dispatchers.IO) {
        val url = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("username", account.username)
            addQueryParameter("password", account.password)
            if (action != null) addQueryParameter("action", action)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }?.build() ?: throw XtreamException("Sunucu adresi geçersiz")
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA).header("Accept", "application/json").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw XtreamException("Sunucu hata verdi (HTTP ${res.code})")
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) throw XtreamException("Sunucu boş yanıt verdi")
            try { AppJson.parseToJsonElement(body) } catch (e: Exception) { throw XtreamException("Sunucu geçerli bir yanıt vermedi") }
        }
    }

    suspend fun userInfo(): UserInfo {
        val root = call(null) as? JsonObject ?: throw XtreamException("Kullanıcı bilgisi alınamadı")
        val u = root.obj("user_info") ?: throw XtreamException("Kullanıcı adı ya da şifre hatalı")
        if (u.int("auth") == 0) throw XtreamException("Kullanıcı adı ya da şifre hatalı")
        return UserInfo(u.str("username"), u.str("status"), u.long("exp_date"), u.int("max_connections"), u.int("active_cons"))
    }

    suspend fun vodCategories() = categories("get_vod_categories")
    suspend fun seriesCategories() = categories("get_series_categories")
    suspend fun liveCategories() = categories("get_live_categories")
    private suspend fun categories(action: String) = call(action).objects().mapNotNull { o ->
        val id = o.str("category_id") ?: return@mapNotNull null
        Category(id, o.str("category_name") ?: id)
    }

    /** Büyük listeler akışla okunur (bkz. StreamJson.kt) */
    private suspend fun <T> stream(action: String, map: (Row) -> T?): List<T> = withContext(Dispatchers.IO) {
        val url = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("username", account.username)
            addQueryParameter("password", account.password)
            addQueryParameter("action", action)
        }?.build() ?: throw XtreamException("Sunucu adresi geçersiz")
        val req = Request.Builder().url(url).header("User-Agent", BROWSER_UA).header("Accept", "application/json").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw XtreamException("Sunucu hata verdi (HTTP ${res.code})")
            val body = res.body ?: throw XtreamException("Sunucu boş yanıt verdi")
            try { readRows(body.byteStream(), map) } catch (e: java.io.IOException) { throw e } catch (e: Exception) { throw XtreamException("Sunucu geçerli bir yanıt vermedi") }
        }
    }

    suspend fun <T> vodStreams(map: (Row) -> T?): List<T> = stream("get_vod_streams", map)
    suspend fun <T> series(map: (Row) -> T?): List<T> = stream("get_series", map)

    suspend fun liveStreams(): List<Channel> = stream("get_live_streams") { o ->
        val id = o.int("stream_id") ?: return@stream null
        Channel(id, o.str("name") ?: "Kanal $id", o.str("stream_icon"), o.str("category_id"), o.int("num") ?: 0, o.str("epg_channel_id"))
    }

    suspend fun vodInfo(id: Int): VodInfo {
        val root = call("get_vod_info", "vod_id" to id.toString()) as? JsonObject ?: return VodInfo()
        val i = root.obj("info") ?: return VodInfo()
        return VodInfo(
            plot = i.str("plot") ?: i.str("description"),
            cast = i.str("cast") ?: i.str("actors"),
            director = i.str("director"),
            genre = i.str("genre"),
            durationSecs = i.int("duration_secs") ?: i.str("duration")?.let { parseClock(it) },
            backdrop = i.firstStr("backdrop_path") ?: i.str("cover_big") ?: i.str("movie_image"),
            releaseDate = i.str("releasedate") ?: i.str("release_date"),
            rating = i.dbl("rating") ?: 0.0,
            tmdbId = i.str("tmdb_id") ?: i.str("tmdb"),
            country = i.str("country"),
            age = i.str("age") ?: i.str("mpaa_rating"),
        )
    }

    suspend fun seriesInfo(id: Int): SeriesInfo {
        val root = call("get_series_info", "series_id" to id.toString()) as? JsonObject ?: throw XtreamException("Bölüm listesi alınamadı")
        val info = root.obj("info")
        val seasons = sortedMapOf<Int, List<Episode>>()
        val eps = root["episodes"]
        // "episodes" bazen { "1": [...] }, bazen [[...], [...]] biçiminde gelir
        val groups: List<Pair<Int?, List<JsonObject>>> = when (eps) {
            is JsonObject -> eps.entries.map { (k, v) -> k.toIntOrNull() to v.objects() }
            is kotlinx.serialization.json.JsonArray -> eps.mapIndexed { i, v -> (i + 1) to v.objects() }
            else -> emptyList()
        }
        for ((key, list) in groups) {
            val parsed = list.mapNotNull { e ->
                val eid = e.str("id") ?: return@mapNotNull null
                val ei = e.obj("info")
                val season = e.int("season") ?: key ?: 1
                Episode(
                    id = eid, season = season, num = e.int("episode_num") ?: 0,
                    title = e.str("title") ?: "", ext = e.str("container_extension") ?: "mp4",
                    plot = ei?.str("plot"), image = ei?.str("movie_image"),
                    durationSecs = ei?.int("duration_secs") ?: ei?.str("duration")?.let { parseClock(it) },
                    tmdbId = ei?.str("tmdb_id"),
                )
            }.sortedBy { it.num }
            if (parsed.isEmpty()) continue
            val s = parsed.first().season
            seasons[s] = (seasons[s].orEmpty() + parsed).sortedBy { it.num }
        }
        if (seasons.isEmpty() && info == null) throw XtreamException("Bölüm listesi boş geldi")
        return SeriesInfo(
            seasons = seasons, plot = info?.str("plot"), cast = info?.str("cast"), director = info?.str("director"),
            genre = info?.str("genre"), backdrop = info?.firstStr("backdrop_path") ?: info?.str("cover"),
            releaseDate = info?.str("releaseDate") ?: info?.str("release_date"), rating = info?.dbl("rating") ?: 0.0,
        )
    }

    suspend fun shortEpg(streamId: Int, limit: Int = 4): List<EpgItem> {
        val root = call("get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString()) as? JsonObject ?: return emptyList()
        val now = System.currentTimeMillis()
        return (root["epg_listings"] ?: return emptyList()).objects().mapNotNull { o ->
            val start = o.long("start_timestamp")?.times(1000) ?: o.str("start")?.let { parseEpgDate(it) } ?: return@mapNotNull null
            val end = o.long("stop_timestamp")?.times(1000) ?: o.str("end")?.let { parseEpgDate(it) } ?: return@mapNotNull null
            EpgItem(b64(o.str("title")), b64(o.str("description")), start, end)
        }.filter { it.end > now }.sortedBy { it.start }
    }

    // --- oynatma adresleri ---
    fun movieUrl(id: Int, ext: String?) = "$base/movie/${enc(account.username)}/${enc(account.password)}/$id.${ext ?: "mp4"}"
    fun episodeUrl(id: String, ext: String?) = "$base/series/${enc(account.username)}/${enc(account.password)}/$id.${ext ?: "mp4"}"
    fun liveUrl(id: Int, hls: Boolean) = "$base/live/${enc(account.username)}/${enc(account.password)}/$id.${if (hls) "m3u8" else "ts"}"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            s = s.removeSuffix("/player_api.php").removeSuffix("/get.php")
            return s.trimEnd('/')
        }
        private fun b64(s: String?): String {
            if (s.isNullOrBlank()) return ""
            return try { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim() } catch (e: Exception) { s }
        }
        private fun parseEpgDate(s: String): Long? = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getDefault() }.parse(s)?.time
        } catch (e: Exception) { null }
        fun parseClock(s: String): Int? {
            val m = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").find(s.trim()) ?: return null
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val c = m.groupValues[3].toIntOrNull()
            return if (c != null) a * 3600 + b * 60 + c else a * 3600 + b * 60
        }
    }
}
