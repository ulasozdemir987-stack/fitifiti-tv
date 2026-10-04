package com.fitifiti.tv.ui

import androidx.compose.runtime.mutableStateListOf
import com.fitifiti.tv.data.xtream.Channel
import com.fitifiti.tv.data.xtream.Episode
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series

/** Ekranlar. Basit yığın: geri tuşu en üsttekini kapatır (navigation-compose'un argüman serileştirmesine gerek yok). */
sealed interface Route {
    data object Login : Route
    data class AddAccount(val editId: String? = null) : Route
    data object Profiles : Route
    data object Main : Route
    data class MovieDetail(val movie: Movie) : Route
    data class SeriesDetail(val series: Series, val focusEpisodeId: String? = null) : Route
    data class CategoryPage(val kind: String, val categoryId: String?, val genre: String? = null) : Route
    data class Player(val req: PlayRequest) : Route
    data class LivePlayer(val channelId: Int, val list: List<Int>) : Route
}

/** Oynatıcıya giden her şey */
data class PlayRequest(
    val kind: String, // movie | episode
    val url: String,
    val key: String,
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val ext: String? = null,
    val startMs: Long = 0,
    val askResume: Boolean = false,
    val movie: Movie? = null,
    val series: Series? = null,
    val episode: Episode? = null,
    val seasons: Map<Int, List<Episode>>? = null,
    val variantSeriesId: Int? = null,
)

class Entry(val id: Long, val route: Route)

class Navigator(start: Route) {
    private var seq = 0L
    val stack = mutableStateListOf(Entry(seq++, start))
    val top get() = stack.last().route
    fun push(r: Route) { com.fitifiti.tv.data.diag.Diag.log("ekran +${describe(r)}"); stack.add(Entry(seq++, r)); noteTop() }
    fun replace(r: Route) { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex); stack.add(Entry(seq++, r)); noteTop() }
    fun reset(r: Route) { stack.clear(); stack.add(Entry(seq++, r)); noteTop() }
    fun back(): Boolean { if (stack.size <= 1) return false; com.fitifiti.tv.data.diag.Diag.log("ekran −${describe(stack.last().route)}"); stack.removeAt(stack.lastIndex); noteTop(); return true }
    /** "Sorun bildir" raporunda hangi ekranda olunduğu */
    private fun noteTop() { com.fitifiti.tv.data.diag.Diag.lastScreen = describe(top) }
    private fun describe(r: Route): String = when (r) {
        is Route.MovieDetail -> "Film: ${r.movie.name}"
        is Route.SeriesDetail -> "Dizi: ${r.series.name}"
        is Route.Player -> "Oynatıcı: ${r.req.title}"
        else -> r::class.simpleName ?: "?"
    }
}
