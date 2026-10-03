package com.fitifiti.tv.data.catalog

import android.content.Context
import com.fitifiti.tv.data.xtream.*
import com.fitifiti.tv.domain.DedupeItem
import com.fitifiti.tv.domain.SearchIndex
import com.fitifiti.tv.domain.categoryRank
import com.fitifiti.tv.domain.dedupe
import com.fitifiti.tv.domain.variantLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@Serializable
data class Catalog(
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val vodCats: List<Category> = emptyList(),
    val seriesCats: List<Category> = emptyList(),
    val liveCats: List<Category> = emptyList(),
    val loadedAt: Long = 0,
) {
    @kotlinx.serialization.Transient val movieById: Map<Int, Movie> = movies.associateBy { it.id }
    @kotlinx.serialization.Transient val seriesById: Map<Int, Series> = series.associateBy { it.id }
    @kotlinx.serialization.Transient val channelById: Map<Int, Channel> = channels.associateBy { it.id }
    @kotlinx.serialization.Transient val catName: Map<String, String> = (vodCats + seriesCats + liveCats).associate { it.id to it.name }
    val isEmpty get() = movies.isEmpty() && series.isEmpty() && channels.isEmpty()
    /** Keşfet sırası: sık türler önce, 4K/dublaj ve yabancı dil kategorileri sona */
    fun sortedCats(cats: List<Category>) = cats.withIndex().sortedWith(compareBy({ categoryRank(it.value.name) }, { it.index })).map { it.value }
}

sealed interface CatalogStatus {
    data object Idle : CatalogStatus
    data class Loading(val step: String, val progress: Float) : CatalogStatus
    data object Ready : CatalogStatus
    data class Error(val message: String) : CatalogStatus
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class CatalogRepository(private val ctx: Context, private val clientFor: (Account) -> XtreamClient) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _catalog = MutableStateFlow(Catalog())
    val catalog: StateFlow<Catalog> = _catalog
    private val _status = MutableStateFlow<CatalogStatus>(CatalogStatus.Idle)
    val status: StateFlow<CatalogStatus> = _status
    private var job: Job? = null
    private var accountId: String? = null
    @Volatile private var index: SearchIndex? = null

    fun search(q: String): SearchIndex.Result {
        val c = _catalog.value
        val idx = index ?: SearchIndex(c.movies, c.series).also { index = it }
        return idx.search(q)
    }

    /** Hesap değişince ya da açılışta: önbellek hemen, ağ arka planda (eski veri 6 saatten yeniyse yine de tazelenir ama beklenmez). */
    fun start(account: Account, force: Boolean = false) {
        if (!force && accountId == account.id && (job?.isActive == true || _status.value == CatalogStatus.Ready)) return
        accountId = account.id
        job?.cancel()
        job = scope.launch {
            val cached = readCache(account.id)
            if (cached != null && !cached.isEmpty) { set(cached); _status.value = CatalogStatus.Ready }
            else { set(Catalog()); _status.value = CatalogStatus.Loading("Kategoriler geliyor…", 0.05f) }
            try {
                val fresh = fetch(clientFor(account), quiet = cached != null && !cached.isEmpty)
                set(fresh); _status.value = CatalogStatus.Ready
                writeCache(account.id, fresh)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) { // bellek yetmezliği dahil: uygulama kapanmasın, hata gösterilsin
                if (_catalog.value.isEmpty) _status.value = CatalogStatus.Error(if (e is OutOfMemoryError) "Katalog bu cihazın belleğine sığmadı" else e.message ?: "Katalog alınamadı")
            }
        }
    }

    fun clear() { job?.cancel(); accountId = null; set(Catalog()); _status.value = CatalogStatus.Idle }

    private fun set(c: Catalog) { _catalog.value = c; index = null }

    private suspend fun fetch(api: XtreamClient, quiet: Boolean): Catalog = coroutineScope {
        fun step(s: String, p: Float) { if (!quiet) _status.value = CatalogStatus.Loading(s, p) }
        val vodCats = async { runCatching { api.vodCategories() }.getOrDefault(emptyList()) }
        val serCats = async { runCatching { api.seriesCategories() }.getOrDefault(emptyList()) }
        val liveCats = async { runCatching { api.liveCategories() }.getOrDefault(emptyList()) }
        val vc = vodCats.await(); val sc = serCats.await(); val lc = liveCats.await()
        step("Filmler geliyor…", 0.25f)
        val movies = parseMovies(api, vc.associate { it.id to it.name })
        step("Diziler geliyor…", 0.6f)
        val series = runCatching { parseSeries(api, sc.associate { it.id to it.name }) }.getOrDefault(emptyList())
        // Film ve diziler hazır: kanallar inerken uygulama açılsın (kanal listesi büyük hesaplarda en uzun adım)
        if (!quiet) { set(Catalog(movies, series, emptyList(), vc, sc, lc, System.currentTimeMillis())); _status.value = CatalogStatus.Ready }
        val channels = runCatching { api.liveStreams() }.getOrDefault(emptyList())
        Catalog(movies, series, channels, vc, sc, lc, System.currentTimeMillis())
    }

    private val yearRe = Regex("(?:19|20)\\d{2}")
    private fun yearOf(vararg s: String?) = s.firstNotNullOfOrNull { v -> v?.let { yearRe.find(it)?.value } }

    private suspend fun parseMovies(api: XtreamClient, cats: Map<String, String>): List<Movie> {
        val items = api.vodStreams { o ->
            val id = o.int("stream_id") ?: return@vodStreams null
            val name = o.str("name") ?: return@vodStreams null
            val ext = o.str("container_extension")
            val rating = o.dbl("rating") ?: o.dbl("rating_5based")?.times(2) ?: 0.0
            val m = Movie(
                id = id, name = name, icon = o.str("stream_icon"), categoryId = o.str("category_id"), ext = ext,
                rating = rating, added = o.long("added") ?: 0, year = yearOf(o.str("year"), o.str("releasedate"), o.str("release_date")),
                genre = o.str("genre"), plot = o.str("plot")?.take(260), tmdb = o.str("tmdb") ?: o.str("tmdb_id"),
                runtimeMin = o.int("episode_run_time"),
            )
            DedupeItem(m, name, m.year ?: "", rating, Variant(id, ext, variantLabel(name, cats[m.categoryId].orEmpty()).ifEmpty { "Standart" }))
        }
        return dedupe(items) { m, v -> m.copy(variants = v) }
    }

    private suspend fun parseSeries(api: XtreamClient, cats: Map<String, String>): List<Series> {
        val items = api.series { o ->
            val id = o.int("series_id") ?: return@series null
            val name = o.str("name") ?: return@series null
            val rating = o.dbl("rating") ?: o.dbl("rating_5based")?.times(2) ?: 0.0
            val s = Series(
                id = id, name = name, cover = o.str("cover"), categoryId = o.str("category_id"), rating = rating,
                added = o.long("last_modified") ?: 0, year = yearOf(o.str("year"), o.str("releaseDate"), o.str("release_date")),
                genre = o.str("genre"), plot = o.str("plot")?.take(260), backdrop = o.firstStr("backdrop_path"), cast = o.str("cast")?.take(120),
            )
            DedupeItem(s, name, s.year ?: "", rating, Variant(id, null, variantLabel(name, cats[s.categoryId].orEmpty()).ifEmpty { "Standart" }))
        }
        return dedupe(items) { s, v -> s.copy(variants = v) }
    }

    private fun cacheFile(accountId: String) = File(ctx.filesDir, "catalog-${accountId.hashCode()}.json")
    private suspend fun readCache(accountId: String): Catalog? = withContext(Dispatchers.IO) {
        // Akışla (dosyanın tamamı bir metin olarak belleğe alınmaz)
        runCatching { cacheFile(accountId).takeIf { it.exists() }?.inputStream()?.buffered()?.use { AppJson.decodeFromStream(Catalog.serializer(), it) } }.getOrNull()
    }
    private suspend fun writeCache(accountId: String, c: Catalog) = withContext(Dispatchers.IO) {
        runCatching {
            val f = cacheFile(accountId); val tmp = File(f.path + ".tmp")
            tmp.outputStream().buffered().use { AppJson.encodeToStream(Catalog.serializer(), c, it) }; tmp.renameTo(f)
        }
    }
}
