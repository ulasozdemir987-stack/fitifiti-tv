package com.fitifiti.tv.data.catalog

import com.fitifiti.tv.data.local.AppDb
import com.fitifiti.tv.data.local.FavoriteEntity
import com.fitifiti.tv.data.local.ProgressEntity
import com.fitifiti.tv.data.local.RecentChannelEntity
import com.fitifiti.tv.data.local.RecentSearchEntity
import com.fitifiti.tv.data.xtream.Episode
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Etkin profilin izleme ilerlemesi, Listem, son kanallar ve aramalar */
@OptIn(ExperimentalCoroutinesApi::class)
class UserData(private val db: AppDb) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val profileId = MutableStateFlow(-1L)

    val progress: StateFlow<List<ProgressEntity>> = profileId.flatMapLatest { if (it < 0) flowOf(emptyList()) else db.progress().observeAll(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val progressMap: StateFlow<Map<String, ProgressEntity>> = progress.map { l -> l.associateBy { it.key } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val favorites: StateFlow<List<FavoriteEntity>> = profileId.flatMapLatest { if (it < 0) flowOf(emptyList()) else db.favorites().observe(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val recentChannels = profileId.flatMapLatest { if (it < 0) flowOf(emptyList()) else db.recent().channels(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val recentSearches = profileId.flatMapLatest { if (it < 0) flowOf(emptyList()) else db.recent().searches(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** "İzlemeye devam et": bitmemiş kayıtlar, dizi başına tek (en yeni) kart */
    val continueList: StateFlow<List<ProgressEntity>> = progress.map { list ->
        val seen = HashSet<Int>()
        list.sortedByDescending { it.updatedAt }.filter { !it.finished && (it.positionMs > 15_000 || it.isUpNext) }.filter { p ->
            val s = p.seriesId ?: return@filter true
            seen.add(s)
        }.take(20)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val pid get() = profileId.value

    fun isFavorite(key: String) = favorites.value.any { it.key == key }
    fun toggleFavorite(movie: Movie) = toggle(FavoriteEntity(pid, "movie-${movie.id}", "movie", movie.id, movie.name, movie.icon))
    fun toggleFavorite(series: Series) = toggle(FavoriteEntity(pid, "series-${series.id}", "series", series.id, series.name, series.cover))
    private fun toggle(f: FavoriteEntity) = scope.launch {
        if (pid < 0) return@launch
        if (isFavorite(f.key)) db.favorites().remove(pid, f.key) else db.favorites().add(f)
    }

    fun saveProgress(e: ProgressEntity) = scope.launch { if (pid >= 0) db.progress().upsert(e.copy(profileId = pid, updatedAt = System.currentTimeMillis())) }
    fun removeProgress(key: String) = scope.launch { if (pid >= 0) db.progress().delete(pid, key) }
    fun markMovieWatched(m: Movie, watched: Boolean) = scope.launch {
        if (pid < 0) return@launch
        val key = "movie-${m.id}"
        if (!watched) { db.progress().delete(pid, key); return@launch }
        val d = ((m.runtimeMin ?: 1) * 60_000L).coerceAtLeast(1000)
        db.progress().upsert(ProgressEntity(pid, key, "movie", m.id.toString(), title = m.name, image = m.icon, ext = m.ext, positionMs = d, durationMs = d))
    }

    /** Bölüm bitince "İzlemeye devam et"e sıradaki bölüm (süre 0 yer tutucu; sitedeki lib/up-next.ts) */
    fun addUpNext(series: Series, next: Episode, image: String?) = scope.launch {
        if (pid < 0) return@launch
        val key = "episode-${next.id}"
        val cur = db.progress().get(pid, key)
        if (cur != null && cur.positionMs > 0) return@launch
        db.progress().upsert(ProgressEntity(pid, key, "episode", next.id, series.id, next.season, next.num, series.name,
            "${next.season}. Sezon · ${next.num}. Bölüm", next.image ?: image ?: series.cover, next.ext))
    }

    fun touchChannel(id: Int) = scope.launch { if (pid >= 0) db.recent().touchChannel(RecentChannelEntity(pid, id)) }
    fun addSearch(q: String) = scope.launch { if (pid >= 0 && q.isNotBlank()) db.recent().addSearch(RecentSearchEntity(pid, q.trim())) }
    fun clearSearches() = scope.launch { if (pid >= 0) db.recent().clearSearches(pid) }
}
