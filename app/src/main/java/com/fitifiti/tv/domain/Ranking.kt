package com.fitifiti.tv.domain

import com.fitifiti.tv.data.catalog.Catalog
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series
import java.util.Calendar

/**
 * Katalogdan türetilen şeritler. Sağlayıcının puanında oy sayısı yok → 9.3+ şüpheli sayılır ve
 * puan ortalamaya çekilir (sitedeki use-charts "quality" yedeği).
 */
class Ranking(c: Catalog) {
    private val year = Calendar.getInstance().get(Calendar.YEAR)

    private fun q(rating: Double) = if (rating <= 0 || rating >= 9.3) 0.0 else 6 + (rating - 6) * 0.45
    private fun recent(y: String?, span: Int) = y?.toIntOrNull()?.let { it >= year - span } ?: false

    val newMovies: List<Movie> = c.movies.sortedByDescending { it.added }.take(30)
    val newSeries: List<Series> = c.series.sortedByDescending { it.added }.take(30)

    val featuredMovies: List<Movie> = c.movies.filter { recent(it.year, 2) && q(it.rating) > 6.6 }.sortedByDescending { q(it.rating) + it.added / 1e12 }.take(30)
        .ifEmpty { newMovies }
    val featuredSeries: List<Series> = c.series.filter { recent(it.year, 3) && q(it.rating) > 6.6 }.sortedByDescending { q(it.rating) + it.added / 1e12 }.take(30)
        .ifEmpty { newSeries }

    val topMovies: List<Movie> = c.movies.filter { q(it.rating) > 0 }.sortedByDescending { q(it.rating) }.take(30)
    val topSeries: List<Series> = c.series.filter { q(it.rating) > 0 }.sortedByDescending { q(it.rating) }.take(30)

    /** En yaygın türler (son eklenen 600 içerikten), tür başına kaliteli + yeni içerikler */
    fun genreRows(kind: String, max: Int = 4): List<Pair<String, List<Any>>> {
        val items: List<Pair<Any, String?>> = if (kind == "movie") newestMovies.map { it to it.genre } else newestSeries.map { it to it.genre }
        val count = HashMap<String, Int>()
        for ((_, g) in items) splitGenres(g).forEach { count[it] = (count[it] ?: 0) + 1 }
        return count.entries.sortedByDescending { it.value }.take(max).mapNotNull { (g, _) ->
            val list = items.filter { (_, gs) -> splitGenres(gs).contains(g) }.map { it.first }.take(24)
            if (list.size >= 6) g to list else null
        }
    }

    private val newestMovies = c.movies.sortedByDescending { it.added }.take(600)
    private val newestSeries = c.series.sortedByDescending { it.added }.take(600)

    companion object {
        private val genreMemo = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
        /** "Aksiyon, Dram / Gerilim" → türler. Aynı tür metni binlerce içerikte tekrarlandığı için önbellekli. */
        fun splitGenres(g: String?): List<String> {
            if (g.isNullOrBlank()) return emptyList()
            return genreMemo.getOrPut(g) { g.split(',', '/', '&', '|').map { titleCaseTr(it.trim()) }.filter { it.length > 1 } }
        }

        @Volatile private var memo: Pair<Long, Ranking>? = null
        fun of(c: Catalog): Ranking {
            memo?.let { (at, r) -> if (at == c.loadedAt && c.loadedAt != 0L) return r }
            return Ranking(c).also { memo = c.loadedAt to it }
        }
    }
}
