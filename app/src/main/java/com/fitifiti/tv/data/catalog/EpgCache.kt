package com.fitifiti.tv.data.catalog

import android.util.Xml
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.EpgEntity
import com.fitifiti.tv.data.xtream.XtreamClient
import com.fitifiti.tv.data.xtream.EpgItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import android.util.Log

object EpgCache {
    private val mutex = Mutex()
    private var lastSync = 0L

    suspend fun syncIfNeeded(client: XtreamClient) {
        val now = System.currentTimeMillis()
        if (now - lastSync < 12 * 3600_000L) return
        mutex.withLock {
            if (now - lastSync < 12 * 3600_000L) return
            try {
                syncXmltv(client)
                lastSync = System.currentTimeMillis()
                App.instance.db.epg().deleteOld()
            } catch (e: Exception) {
                Log.e("EpgCache", "XMLTV sync failed: " + e.message)
            }
        }
    }

    private suspend fun syncXmltv(client: XtreamClient) = withContext(Dispatchers.IO) {
        val url = "${client.base}/xmltv.php?username=${client.account.username}&password=${client.account.password}"
        val req = Request.Builder().url(url).build()
        App.instance.http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
            val ins = res.body?.byteStream() ?: throw Exception("Empty body")
            parseAndInsertStream(ins)
        }
    }

    private suspend fun parseAndInsertStream(ins: InputStream) = withContext(Dispatchers.IO) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ins, null)

        val batch = mutableListOf<EpgEntity>()
        val db = App.instance.db.epg()

        val now = System.currentTimeMillis()
        val minTime = now - 6 * 3600_000L
        val maxTime = now + 48 * 3600_000L

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                val startStr = parser.getAttributeValue(null, "start")
                val endStr = parser.getAttributeValue(null, "stop")
                val channel = parser.getAttributeValue(null, "channel")

                val start = parseXmltvDate(startStr)
                val end = parseXmltvDate(endStr)

                var title = ""
                var desc = ""

                // Read inner tags
                while (true) {
                    eventType = parser.next()
                    if (eventType == XmlPullParser.END_TAG && parser.name == "programme") break
                    if (eventType == XmlPullParser.START_TAG) {
                        val name = parser.name
                        if (name == "title") {
                            title = parser.nextText()
                        } else if (name == "desc") {
                            desc = parser.nextText()
                        }
                    }
                }

                if (channel != null && start != null && end != null && end > minTime && start < maxTime) {
                    batch.add(EpgEntity(channel, start, end, title, desc))
                }

                if (batch.size >= 1000) {
                    db.insertAll(batch)
                    batch.clear()
                }
            } else {
                eventType = parser.next()
            }
        }
        if (batch.isNotEmpty()) {
            db.insertAll(batch)
        }
    }

    // Format: "20240925183000 +0300" or just "20240925183000"
    private fun parseXmltvDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            val dateStr = s.take(14)
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            val tzIndex = s.indexOf('+').takeIf { it > 0 } ?: s.indexOf('-').takeIf { it > 0 }
            if (tzIndex != null && tzIndex + 4 < s.length) {
                sdf.timeZone = TimeZone.getTimeZone("GMT" + s.substring(tzIndex, tzIndex + 5))
            } else {
                sdf.timeZone = TimeZone.getDefault()
            }
            sdf.parse(dateStr)?.time
        } catch (e: Exception) { null }
    }

    suspend fun getNowNext(epgChannelId: String?, streamId: Int): List<EpgItem> {
        val client = App.instance.client()
        syncIfNeeded(client)
        if (epgChannelId != null) {
            val entities = App.instance.db.epg().nowNext(epgChannelId)
            if (entities.isNotEmpty()) {
                return entities.map { EpgItem(it.title, it.desc, it.start, it.end) }
            }
        }
        // Fallback to Xtream if no DB data
        return try { client.shortEpg(streamId, 2) } catch (e: Exception) { emptyList() }
    }

    suspend fun range(channelIds: List<String>, from: Long, to: Long): Map<String, List<EpgItem>> {
        val client = App.instance.client()
        syncIfNeeded(client)
        val entities = App.instance.db.epg().range(channelIds, from, to)
        return entities.groupBy { it.channel }.mapValues { (_, list) ->
            list.map { EpgItem(it.title, it.desc, it.start, it.end) }
        }
    }
    
    suspend fun search(query: String): List<EpgItem> {
        if (query.length < 3) return emptyList()
        val entities = App.instance.db.epg().search(query)
        return entities.map { EpgItem(it.title, it.desc, it.start, it.end) }
    }

    fun clear() {
        // App.instance.db.epg().clearAll() - Usually no need to clear manually
    }
}

fun List<EpgItem>.now(t: Long = System.currentTimeMillis()) = firstOrNull { it.start <= t && it.end > t }
fun List<EpgItem>.next(t: Long = System.currentTimeMillis()) = firstOrNull { it.start > t }
fun EpgItem.fraction(t: Long = System.currentTimeMillis()) = if (end > start) ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f
