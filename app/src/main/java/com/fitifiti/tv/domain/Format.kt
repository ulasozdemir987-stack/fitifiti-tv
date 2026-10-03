package com.fitifiti.tv.domain

import java.util.Locale

private val TITLE_TAG = Regex("^(?:tr\\s*-?\\s*sub|trsub|tr\\s*altyaz[ıi]l[ıi]|altyaz[ıi]l[ıi]|tr\\s*dublaj|dublaj|dual|4k(?:\\s*(?:hdr|uhd|dv))*|uhd|hdr|dolby\\s*vision|1080p|720p|2160p|f?hd|imax)$", RegexOption.IGNORE_CASE)
private val TR_CHARS = Regex("[çğıöşüÇĞİÖŞÜ]")

fun hasTr(s: String): Boolean {
    return TR_CHARS.containsMatchIn(s) || Regex("(?:^|\\s)(?:ve|bir|ile|için|ne|bu|şu|da|de|mi|gibi|kadar|olan)(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(s)
}

fun displayTitle(value: Any?): String {
    val str = (value?.toString() ?: "").trim()
    var res = str.replace(Regex("\\s*[(\\[【]\\s*(?:19|20)\\d{2}\\s*[)\\]】]\\s*$"), "")
    res = res.replace(Regex("\\s*\\[[^\\]]*\\]\\s*"), " ")
    res = res.replace(Regex("\\s{2,}"), " ")
    return res.trim()
}

fun fixShouting(s: String): String {
    val letters = s.replace(Regex("[^\\p{L}]"), "")
    val trLocale = Locale("tr", "TR")
    if (letters.length < 6 || letters != letters.uppercase(trLocale) || !s.trim().contains(Regex("\\s"))) return s
    
    val loc = if (TR_CHARS.containsMatchIn(s)) trLocale else Locale.US
    return s.lowercase(loc).replace(Regex("(^|[\\s(/:.-])(\\p{L})")) { matchResult ->
        matchResult.groupValues[1] + matchResult.groupValues[2].uppercase(loc)
    }
}

fun splitTitle(value: Any?): Pair<String, String> {
    val base = displayTitle(value).replace(Regex("\\|\\s*4K[^|\\]]*\\]?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s{2,}"), " ").trim()
        
    val parts = base.split(Regex("\\s+[-–]\\s+"))
        .map { it.replace(Regex("\\s+(?:tr\\s*-?\\s*sub|trsub|4k\\s*hdr)$", RegexOption.IGNORE_CASE), "").trim() }
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
    
    val clockMatch = Regex("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$").find(raw)
    if (clockMatch != null) {
        val h = clockMatch.groupValues[1].toInt()
        val m = clockMatch.groupValues[2].toInt()
        val s = clockMatch.groups[3]?.value?.toIntOrNull()
        seconds = if (s != null) (h * 3600 + m * 60 + s).toDouble() else (h * 60 + m).toDouble()
    } else {
        val n = raw.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return ""
        seconds = if (Regex("min|dk|dak", RegexOption.IGNORE_CASE).containsMatchIn(raw) || unit == "minutes") n * 60 else n
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
        .replace(Regex("\\p{M}+"), "")
    return s.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

val EP_MARK = "(?:s\\d+\\s*e\\d+|\\d+x\\d+|\\d+\\.?\\s*sezon|sezon\\s*\\d+|\\d+\\.?\\s*bölüm|bölüm\\s*\\d+|\\d+\\.?\\s*bolum|bolum\\s*\\d+|episode\\s*\\d+|ep\\.?\\s*\\d+)"

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
    val edge = Regex("^[\\s\\-–:|·,]*$EP_MARK[\\s\\-–:|·,]*", RegexOption.IGNORE_CASE)
    val tail = Regex("[\\s\\-–:|·,]*$EP_MARK[\\s\\-–:|·,]*$", RegexOption.IGNORE_CASE)
    
    for (k in 0..3) {
        val before = t
        t = t.replace(edge, "").replace(tail, "")
        if (t == before) break
    }
    t = t.replace(Regex("^[\\s\\-–:|·,]+|[\\s\\-–:|·,]+$"), "").trim()
    
    if (t.isEmpty() || Regex("^\\d+$").matches(t) || (episodeNum != null && t == episodeNum)) return ""
    if (series.isNotEmpty() && foldTr(t) == foldTr(series)) return ""
    return t
}
