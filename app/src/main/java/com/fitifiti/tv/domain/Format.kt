package com.fitifiti.tv.domain

import java.util.Locale

// Sık çağrılan işlevlerin kalıpları bir kez derlenir (TV işlemcisinde binlerce kez derlemek donmaya yol açıyordu)
private val RX0 = Regex("(?:^|\\s)(?:ve|bir|ile|için|ne|bu|şu|da|de|mi|gibi|kadar|olan)(?:\\s|$)", RegexOption.IGNORE_CASE)
private val RX1 = Regex("\\s*[(\\[【]\\s*(?:19|20)\\d{2}\\s*[)\\]】]\\s*$")
private val RX2 = Regex("\\s*\\[[^\\]]*\\]\\s*")
private val RX3 = Regex("\\s{2,}")
private val RX4 = Regex("[^\\p{L}]")
private val RX5 = Regex("\\s")
private val RX6 = Regex("(^|[\\s(/:.-])(\\p{L})")
private val RX7 = Regex("\\|\\s*4K[^|\\]]*\\]?", RegexOption.IGNORE_CASE)
private val RX8 = Regex("\\s+[-–]\\s+")
private val RX9 = Regex("\\s+(?:tr\\s*-?\\s*sub|trsub|4k\\s*hdr)$", RegexOption.IGNORE_CASE)
private val RX10 = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$")
private val RX11 = Regex("[^0-9.]")
private val RX12 = Regex("min|dk|dak", RegexOption.IGNORE_CASE)
private val RX13 = Regex("\\p{M}+")
private val RX14 = Regex("[^\\p{L}\\p{N}]+")
private val RX17 = Regex("^[\\s\\-–:|·,]+|[\\s\\-–:|·,]+$")
private val RX18 = Regex("^\\d+$")

private val TITLE_TAG = Regex("^(?:tr\\s*-?\\s*sub|trsub|tr\\s*altyaz[ıi]l[ıi]|altyaz[ıi]l[ıi]|tr\\s*dublaj|dublaj|dual|4k(?:\\s*(?:hdr|uhd|dv))*|uhd|hdr|dolby\\s*vision|1080p|720p|2160p|f?hd|imax)$", RegexOption.IGNORE_CASE)
private val TR_CHARS = Regex("[çğıöşüÇĞİÖŞÜ]")

fun hasTr(s: String): Boolean {
    return TR_CHARS.containsMatchIn(s) || RX0.containsMatchIn(s)
}

fun displayTitle(value: Any?): String {
    val str = (value?.toString() ?: "").trim()
    var res = str.replace(RX1, "")
    res = res.replace(RX2, " ")
    res = res.replace(RX3, " ")
    return res.trim()
}

fun fixShouting(s: String): String {
    val letters = s.replace(RX4, "")
    val trLocale = Locale("tr", "TR")
    if (letters.length < 6 || letters != letters.uppercase(trLocale) || !s.trim().contains(RX5)) return s
    
    val loc = if (TR_CHARS.containsMatchIn(s)) trLocale else Locale.US
    return s.lowercase(loc).replace(RX6) { matchResult ->
        matchResult.groupValues[1] + matchResult.groupValues[2].uppercase(loc)
    }
}

fun splitTitle(value: Any?): Pair<String, String> {
    val base = displayTitle(value).replace(RX7, "")
        .replace(RX3, " ").trim()
        
    val parts = base.split(RX8)
        .map { it.replace(RX9, "").trim() }
        .filter { it.isNotEmpty() && !TITLE_TAG.matches(it) }
        
    if (parts.size <= 1) return Pair(fixShouting(parts.firstOrNull() ?: base), "")
    
    val trIdx = parts.indexOfFirst { hasTr(it) }
    val mainIdx = if (trIdx > 0 && !hasTr(parts[0])) trIdx else 0
    val title = parts[mainIdx]
    
    val rest = parts.filterIndexed { index, _ -> index != mainIdx }
    val alt = rest.reversed().find { !hasTr(it) } ?: rest.joinToString(" - ")
    
    if (alt.equals(title, ignoreCase = true)) return Pair(fixShouting(title), "")
    return Pair(fixShouting(title), alt)
}

fun cardTitle(value: Any?): String = splitTitle(value).first

fun formatDuration(value: Any?, unit: String = "seconds"): String {
    if (value == null || value.toString().isBlank()) return ""
    val raw = value.toString().trim()
    var seconds: Double = 0.0
    
    val clockMatch = RX10.find(raw)
    if (clockMatch != null) {
        val h = clockMatch.groupValues[1].toInt()
        val m = clockMatch.groupValues[2].toInt()
        val s = clockMatch.groups[3]?.value?.toIntOrNull()
        seconds = if (s != null) (h * 3600 + m * 60 + s).toDouble() else (h * 60 + m).toDouble()
    } else {
        val n = raw.replace(RX11, "").toDoubleOrNull() ?: return ""
        seconds = if (RX12.containsMatchIn(raw) || unit == "minutes") n * 60 else n
    }
    
    val minutes = Math.round(seconds / 60).toInt()
    if (minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    if (h == 0) return "$m dk"
    return if (m > 0) "$h sa $m dk" else "$h sa"
}

/** Türkçe/aksan duyarsız karşılaştırma anahtarı ("Gönül Dağı" → "gonul dagi") */
fun foldTr(v: String): String {
    val s = java.text.Normalizer.normalize(v.lowercase(Locale("tr", "TR")).replace("ı", "i"), java.text.Normalizer.Form.NFD)
        .replace(RX13, "")
    return s.replace(RX14, " ").trim()
}

val EP_MARK = "(?:s\\d+\\s*e\\d+|\\d+x\\d+|\\d+\\.?\\s*sezon|sezon\\s*\\d+|\\d+\\.?\\s*bölüm|bölüm\\s*\\d+|\\d+\\.?\\s*bolum|bolum\\s*\\d+|episode\\s*\\d+|ep\\.?\\s*\\d+)"
private val RX15 = Regex("^[\\s\\-–:|·,]*$EP_MARK[\\s\\-–:|·,]*", RegexOption.IGNORE_CASE)
private val RX16 = Regex("[\\s\\-–:|·,]*$EP_MARK[\\s\\-–:|·,]*$", RegexOption.IGNORE_CASE)

fun episodeName(title: Any?, seriesName: Any?, episodeNum: String? = null): String {
    var t = displayTitle(title)
    val series = displayTitle(seriesName)
    if (series.isNotEmpty() && foldTr(t).startsWith(foldTr(series))) {
        var i = 0
        var seen = ""
        while (i < t.length && foldTr(seen) != foldTr(series)) {
            seen += t[i]
            i++
        }
        t = t.substring(i)
    }
    val edge = RX15
    val tail = RX16
    
    for (k in 0..3) {
        val before = t
        t = t.replace(edge, "").replace(tail, "")
        if (t == before) break
    }
    t = t.replace(RX17, "").trim()
    
    if (t.isEmpty() || RX18.matches(t) || (episodeNum != null && t == episodeNum)) return ""
    if (series.isNotEmpty() && foldTr(t) == foldTr(series)) return ""
    return t
}


private val TR_GENRES = mapOf(
    "action" to "Aksiyon", "adventure" to "Macera", "animation" to "Animasyon",
    "comedy" to "Komedi", "crime" to "Suç", "documentary" to "Belgesel",
    "drama" to "Dram", "family" to "Aile", "fantasy" to "Fantastik",
    "history" to "Tarih", "horror" to "Korku", "music" to "Müzik",
    "musical" to "Müzikal", "mystery" to "Gizem", "romance" to "Romantik",
    "romantic" to "Romantik", "sci-fi" to "Bilim Kurgu", "science fiction" to "Bilim Kurgu",
    "scifi" to "Bilim Kurgu", "thriller" to "Gerilim", "war" to "Savaş",
    "western" to "Vahşi Batı", "biography" to "Biyografi", "sport" to "Spor",
    "sports" to "Spor", "news" to "Haber", "reality" to "Reality",
    "talk" to "Sohbet", "short" to "Kısa Film"
)

fun trGenre(raw: String): String {
    val clean = raw.trim()
    val lower = clean.lowercase(Locale.ENGLISH)
    return TR_GENRES[lower] ?: fixShouting(clean)
}

fun formatGenres(genre: String?, limit: Int = 3): String? {
    if (genre.isNullOrBlank()) return null
    val list = genre.split(',', '/', '&', '|', ';')
        .map { it.trim() }
        .filter { it.length > 1 }
        .map { trGenre(it) }
        .distinct()
        .take(limit)
    return if (list.isEmpty()) null else list.joinToString(", ")
}
