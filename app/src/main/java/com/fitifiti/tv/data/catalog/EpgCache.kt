package com.fitifiti.tv.data.catalog

import com.fitifiti.tv.App
import com.fitifiti.tv.data.xtream.EpgItem
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Kanal başına kısa yayın akışı (şimdi + sonra…), 5 dk bellek önbelleği, aynı anda en fazla 4 istek */
object EpgCache {
    private class Hit(val at: Long, val items: List<EpgItem>)
    private val cache = ConcurrentHashMap<Int, Hit>()
    private val gate = Semaphore(4)

    fun cached(id: Int): List<EpgItem>? = cache[id]?.takeIf { System.currentTimeMillis() - it.at < 5 * 60_000 }?.items?.filter { it.end > System.currentTimeMillis() }

    suspend fun get(id: Int, limit: Int = 4): List<EpgItem> {
        cached(id)?.let { if (it.size >= minOf(limit, 2) || it.isEmpty()) return it }
        val items = gate.withPermit { runCatching { App.instance.client().shortEpg(id, limit) }.getOrDefault(emptyList()) }
        cache[id] = Hit(System.currentTimeMillis(), items)
        return items
    }

    fun clear() = cache.clear()
}

fun List<EpgItem>.now(t: Long = System.currentTimeMillis()) = firstOrNull { it.start <= t && it.end > t }
fun List<EpgItem>.next(t: Long = System.currentTimeMillis()) = firstOrNull { it.start > t }
fun EpgItem.fraction(t: Long = System.currentTimeMillis()) = if (end > start) ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
