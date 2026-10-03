package com.fitifiti.tv.domain

import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series

// Sık çağrılan işlevlerin kalıpları bir kez derlenir (TV işlemcisinde binlerce kez derlemek donmaya yol açıyordu)
private val RX0 = Regex("\\[[^\\]]*\\]")
private val RX1 = Regex("\\((?:19|20)\\d{2}\\)")
private val RX2 = Regex("(?:19|20)\\d{2}")
private val RX3 = Regex("^\\d{4}$")

// Türkçe/aksan duyarsız, 1-2 harf yazım hatasına toleranslı, kelime sırasından bağımsız arama (sitedeki lib/search-index.ts).
class SearchIndex(movies: List<Movie>, series: List<Series>) {
    private class Item<T>(val raw: T, val f: String, val tokens: List<String>, val year: String, val rating: Double)
    private val m = movies.map { item(it, it.name, it.year, it.rating) }
    private val s = series.map { item(it, it.name, it.year, it.rating) }

    private fun <T> item(raw: T, name: String, year: String?, rating: Double): Item<T> {
        val f = foldTr(name.replace(RX0, " ").replace(RX1, " "))
        val y = RX2.find(year.orEmpty())?.value ?: ""
        return Item(raw, f, f.split(' ').filter { it.isNotEmpty() }, y, rating.coerceAtMost(10.0))
    }

    data class Result(val movies: List<Movie>, val series: List<Series>, val fuzzy: Boolean)

    fun search(q: String, limit: Int = 40): Result {
        val full = foldTr(q)
        val qt = full.split(' ').filter { it.isNotEmpty() }
        if (qt.isEmpty()) return Result(emptyList(), emptyList(), false)
        val rm = rank(m, qt, full); val rs = rank(s, qt, full)
        val fuzzy = rm.size + rs.size > 0 && rm.take(limit).none { it.second } && rs.take(limit).none { it.second }
        return Result(rm.take(limit).map { it.first }, rs.take(limit).map { it.first }, fuzzy)
    }

    private fun <T> rank(items: List<Item<T>>, qTokens: List<String>, full: String): List<Pair<T, Boolean>> {
        val allowMiss = if (qTokens.size >= 5) 1 else 0
        val out = ArrayList<Triple<T, Double, Boolean>>()
        for (it in items) {
            var score = 0.0; var miss = 0; var direct = true; var dead = false
            for (q in qTokens) {
                var best = if (RX3.matches(q) && it.year == q) 3.0 else 0.0
                for (nt in it.tokens) { val sc = tokenScore(q, nt); if (sc > best) best = sc; if (best >= 4) break }
                if (best == 0.0) { miss++; if (miss > allowMiss) { dead = true; break }; continue }
                if (best == 2.0 || best == 2.2) direct = false
                score += best
            }
            if (dead || score <= 0) continue
            score += if (it.f.startsWith(full)) 3.0 else if (it.f.contains(full)) 1.5 else 0.0
            score += it.rating * 0.12 - it.f.length * 0.01
            out += Triple(it.raw, score, direct)
        }
        return out.sortedByDescending { it.second }.map { it.first to it.third }
    }

    private fun tokenScore(q: String, n: String): Double {
        if (n == q) return 4.0
        if (n.startsWith(q)) return 3.2
        if (q.length >= 3 && n.contains(q)) return 1.6
        if (q.length >= 4) {
            val max = if (q.length >= 8) 2 else 1
            if (editLE(q, n, max) <= max) return 2.2
            if (n.length > q.length && editLE(q, n.take(q.length), max) <= max) return 2.0
        }
        return 0.0
    }

    /** Sınırlı Damerau-Levenshtein */
    private fun editLE(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v; if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            prev2 = prev; prev = cur
        }
        return prev[b.length]
    }
}
