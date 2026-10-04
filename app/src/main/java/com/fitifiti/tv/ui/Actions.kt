package com.fitifiti.tv.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ProgressEntity
import com.fitifiti.tv.data.xtream.Episode
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series
import com.fitifiti.tv.data.xtream.Variant

val LocalActions = staticCompositionLocalOf<Actions> { error("Actions yok") }

/** Ekranların ortak eylemleri: detay aç, oynat, kategoriye git, kanal aç */
class Actions(val nav: Navigator) {
    private val app get() = App.instance

    fun openMovie(m: Movie) = nav.push(Route.MovieDetail(m))
    fun openSeries(s: Series, focusEpisodeId: String? = null) = nav.push(Route.SeriesDetail(s, focusEpisodeId))
    fun openCategory(kind: String, categoryId: String?, genre: String? = null) = nav.push(Route.CategoryPage(kind, categoryId, genre))
    fun openPlatform(brand: String) = nav.push(Route.Platform(brand))
    fun playChannel(id: Int, list: List<Int>) { app.user.touchChannel(id); nav.push(Route.LivePlayer(id)) }

    /** Seçili sürüm: içerik başına seçim → genel tercih (dublaj/4K benzerliği) → tutulan kopya */
    fun chosenVariant(key: String, variants: List<Variant>): Variant? {
        if (variants.size < 2) return null
        app.settings.variantFor(key)?.let { l -> variants.firstOrNull { it.label == l }?.let { return it } }
        val pref = app.settings.variantPref() ?: return null
        fun score(l: String): Int {
            var s = 0
            if (l == pref) s += 10
            if (l.contains("dublaj", true) == pref.contains("dublaj", true)) s += 3
            if (l.contains("4K") == pref.contains("4K")) s += 2
            return s
        }
        return variants.maxByOrNull { score(it.label) }
    }

    fun playMovie(m: Movie, fromStart: Boolean = false) {
        val key = "movie-${m.id}"
        val v = chosenVariant(key, m.variants)
        val p = app.user.progressMap.value[key]
        val resume = !fromStart && p != null && !p.finished && p.positionMs > 15_000
        nav.push(Route.Player(PlayRequest(
            kind = "movie", url = app.client().movieUrl(v?.id ?: m.id, v?.ext ?: m.ext), key = key, itemId = m.id.toString(),
            title = m.name, image = m.icon, ext = v?.ext ?: m.ext, startMs = if (resume) p!!.positionMs else 0, askResume = resume, movie = m,
        )))
    }

    fun playEpisode(s: Series, ep: Episode, seasons: Map<Int, List<Episode>>?, variantSeriesId: Int? = null, fromStart: Boolean = false, replace: Boolean = false) {
        val key = "episode-${ep.id}"
        val p = app.user.progressMap.value[key]
        val resume = !fromStart && p != null && !p.finished && p.positionMs > 15_000
        val req = PlayRequest(
            kind = "episode", url = app.client().episodeUrl(ep.id, ep.ext), key = key, itemId = ep.id,
            title = s.name, subtitle = "${ep.season}. Sezon · ${ep.num}. Bölüm" + com.fitifiti.tv.domain.episodeName(ep.title, s.name).let { if (it.isNotBlank() && !it.matches(Regex("(?i).*bölüm\\s*\\d+.*"))) " · $it" else "" },
            image = ep.image ?: s.cover, ext = ep.ext, startMs = if (resume) p!!.positionMs else 0, askResume = resume,
            series = s, episode = ep, seasons = seasons, variantSeriesId = variantSeriesId,
        )
        if (replace) nav.replace(Route.Player(req)) else nav.push(Route.Player(req))
    }

    /** "İzlemeye devam et" kartı */
    fun playContinue(p: ProgressEntity) {
        val cat = app.catalog.catalog.value
        if (p.kind == "movie") {
            val m = cat.movieById[p.itemId.toIntOrNull() ?: -1]
            if (m != null) { playMovie(m); return }
            nav.push(Route.Player(PlayRequest("movie", app.client().movieUrl(p.itemId.toInt(), p.ext), p.key, p.itemId, p.title, image = p.image, ext = p.ext,
                startMs = p.positionMs, askResume = p.positionMs > 15_000)))
            return
        }
        val s = p.seriesId?.let { cat.seriesById[it] }
        val ep = Episode(p.itemId, p.season ?: 1, p.episodeNum ?: 0, "", p.ext ?: "mp4", image = p.image)
        if (s != null) {
            val v = chosenVariant("series-${s.id}", s.variants)
            playEpisode(s, ep, null, v?.id)
        } else {
            nav.push(Route.Player(PlayRequest("episode", app.client().episodeUrl(p.itemId, p.ext), p.key, p.itemId, p.title, p.subtitle, p.image, p.ext,
                startMs = p.positionMs, askResume = p.positionMs > 15_000)))
        }
    }
}
