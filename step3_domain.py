import os
from pathlib import Path

def write_file(path, content):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        f.write(content.strip() + '\n')

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv\domain\format"

# --- Format.kt ---
write_file(f"{base_dir}/Format.kt", """
package com.fitifiti.tv.domain.format

import java.util.Locale

private val TITLE_TAG = Regex("^(?:tr\\\\s*-?\\\\s*sub|trsub|tr\\\\s*altyaz[ıi]l[ıi]|altyaz[ıi]l[ıi]|tr\\\\s*dublaj|dublaj|dual|4k(?:\\\\s*(?:hdr|uhd|dv))*|uhd|hdr|dolby\\\\s*vision|1080p|720p|2160p|f?hd|imax)$", RegexOption.IGNORE_CASE)
private val TR_CHARS = Regex("[çğıöşüÇĞİÖŞÜ]")

fun hasTr(s: String): Boolean {
    return TR_CHARS.containsMatchIn(s) || Regex("(?:^|\\\\s)(?:ve|bir|ile|için|ne|bu|şu|da|de|mi|gibi|kadar|olan)(?:\\\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(s)
}

fun displayTitle(value: Any?): String {
    val str = (value?.toString() ?: "").trim()
    var res = str.replace(Regex("\\\\s*[([【]\\\\s*(?:19|20)\\\\d{2}\\\\s*[)\\\\]】]\\\\s*$"), "")
    res = res.replace(Regex("\\\\s*\\\\[[^\\\\]]*\\\\]\\\\s*"), " ")
    res = res.replace(Regex("\\\\s{2,}"), " ")
    return res.trim()
}

fun fixShouting(s: String): String {
    val letters = s.replace(Regex("[^\\\\p{L}]"), "")
    val trLocale = Locale("tr", "TR")
    if (letters.length < 6 || letters != letters.uppercase(trLocale) || !s.trim().contains(Regex("\\\\s"))) return s
    
    val loc = if (TR_CHARS.containsMatchIn(s)) trLocale else Locale.US
    return s.lowercase(loc).replace(Regex("(^|[\\\\s(/:.-])(\\\\p{L})")) { matchResult ->
        matchResult.groupValues[1] + matchResult.groupValues[2].uppercase(loc)
    }
}

fun splitTitle(value: Any?): Pair<String, String> {
    val base = displayTitle(value).replace(Regex("\\\\|\\\\s*4K[^|\\\\]]*\\\\]?", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\\\s{2,}"), " ").trim()
        
    val parts = base.split(Regex("\\\\s+[-–]\\\\s+"))
        .map { it.replace(Regex("\\\\s+(?:tr\\\\s*-?\\\\s*sub|trsub|4k\\\\s*hdr)$", RegexOption.IGNORE_CASE), "").trim() }
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
    
    val clockMatch = Regex("^(\\\\d{1,2}):(\\\\d{2})(?::(\\\\d{2}))?$").find(raw)
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

fun foldTr(v: String): String {
    // simplified version of foldTr for Android
    var s = v.lowercase(Locale("tr", "TR"))
        .replace("ı", "i")
        .replace("ğ", "g")
        .replace("ü", "u")
        .replace("ş", "s")
        .replace("ö", "o")
        .replace("ç", "c")
    s = s.replace(Regex("[^a-z0-9]+"), " ")
    return s.trim()
}

val EP_MARK = "(?:s\\\\d+\\\\s*e\\\\d+|\\\\d+x\\\\d+|\\\\d+\\\\.?\\\\s*sezon|sezon\\\\s*\\\\d+|\\\\d+\\\\.?\\\\s*bölüm|bölüm\\\\s*\\\\d+|\\\\d+\\\\.?\\\\s*bolum|bolum\\\\s*\\\\d+|episode\\\\s*\\\\d+|ep\\\\.?\\\\s*\\\\d+)"

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
    val edge = Regex("^[\\\\s\\\\-–:|·,]*$EP_MARK[\\\\s\\\\-–:|·,]*", RegexOption.IGNORE_CASE)
    val tail = Regex("[\\\\s\\\\-–:|·,]*$EP_MARK[\\\\s\\\\-–:|·,]*$", RegexOption.IGNORE_CASE)
    
    for (k in 0..3) {
        val before = t
        t = t.replace(edge, "").replace(tail, "")
        if (t == before) break
    }
    t = t.replace(Regex("^[\\\\s\\\\-–:|·,]+|[\\\\s\\\\-–:|·,]+$"), "").trim()
    
    if (t.isEmpty() || Regex("^\\\\d+$").matches(t) || (episodeNum != null && t == episodeNum)) return ""
    if (series.isNotEmpty() && foldTr(t) == foldTr(series)) return ""
    return t
}
""")

# --- CategoryStyle.kt ---
write_file(f"{base_dir}/CategoryStyle.kt", """
package com.fitifiti.tv.domain.format

data class CategoryStyle(
    val label: String,
    val color: String,
    val platform: Boolean
)

fun hashColor(s: String): String {
    val hues = listOf("#8b5cf6", "#2dd4bf", "#6366f1", "#ec4899", "#f59e0b", "#22c55e", "#0ea5e9", "#f43f5e")
    var h = 0L
    for (c in s) {
        h = (h * 31 + c.code.toLong()) and 0xFFFFFFFF
    }
    return hues[(h % hues.size).toInt()]
}

fun categoryStyle(rawName: String?): CategoryStyle {
    val name = (rawName ?: "").trim()
    
    // Quick brand matches
    if (Regex("^netflix", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Netflix", "#e50914", true)
    if (Regex("^exxen", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Exxen", "#f5c518", true)
    if (Regex("^disney", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Disney+", "#1f4fff", true)
    if (Regex("^amazon", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Prime Video", "#00a8e1", true)
    if (Regex("^tab[iı]{1,2}\\\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("tabii", "#00c2a8", true)
    if (Regex("^hbo", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("HBO", "#7b2ff7", true)
    if (Regex("^max\\\\b.*blu", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Max · BluTV", "#0046ff", true)
    if (Regex("^apple", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Apple TV+", "#a3a3a3", true)
    
    val label = name // fallback
    return CategoryStyle(label, hashColor(label), false)
}

fun categoryRank(rawName: String?): Int {
    val n = rawName ?: ""
    val featured = listOf(
        Regex("^v[iİı]zyon", RegexOption.IGNORE_CASE),
        Regex("^imdb", RegexOption.IGNORE_CASE),
        Regex("aks[iİı]yon", RegexOption.IGNORE_CASE),
        Regex("komed[iİı]", RegexOption.IGNORE_CASE),
        Regex("^dram", RegexOption.IGNORE_CASE),
        Regex("^a[iİı]le", RegexOption.IGNORE_CASE),
        Regex("pol[iİı]s[iİı]ye", RegexOption.IGNORE_CASE),
        Regex("korku", RegexOption.IGNORE_CASE),
        Regex("b[iİı]l[iİı]m\\\\s*kurgu", RegexOption.IGNORE_CASE),
        Regex("^yerl[iİı]\\\\s*f[iİı]lm", RegexOption.IGNORE_CASE)
    )
    
    val fIdx = featured.indexOfFirst { it.containsMatchIn(n) }
    if (fIdx >= 0) return fIdx
    
    if (Regex("^apple", RegexOption.IGNORE_CASE).containsMatchIn(n)) return 99
    if (Regex("^4k\\\\b|dublaj|altyaz[iİı]l[iİı]", RegexOption.IGNORE_CASE).containsMatchIn(n)) return 200
    
    return 100
}
""")

# --- CatalogDedupe.kt ---
write_file(f"{base_dir}/CatalogDedupe.kt", """
package com.fitifiti.tv.domain.format

import java.util.Locale

fun normalizeTitleKey(raw: Any?): String {
    var str = (raw?.toString() ?: "")
    str = str.replace(Regex("\\\\s*[([]\\\\s*(?:19|20)\\\\d{2}\\\\s*[)\\\\]]\\\\s*$"), "")
    str = str.replace(Regex("\\\\[[^\\\\]]*\\\\]"), " ")
    str = str.replace(Regex("\\\\s+-\\\\s+.+$"), "")
    return foldTr(str) // Reusing foldTr from Format.kt for simplicity
}

data class Variant(val id: Int, val ext: String? = null, val label: String)

fun variantLabel(name: String, categoryName: String = ""): String {
    val n = "$name $categoryName"
    val parts = mutableListOf<String>()
    if (Regex("\\\\b(4k|uhd|2160p?)\\\\b", RegexOption.IGNORE_CASE).containsMatchIn(n)) {
        parts.add(if (Regex("hdr|dolby|\\\\bdv\\\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) "4K HDR" else "4K")
    }
    if (Regex("tr\\\\s*dub|dublaj", RegexOption.IGNORE_CASE).containsMatchIn(n)) {
        parts.add("Türkçe dublaj")
    } else if (Regex("tr\\\\s*sub|altyaz", RegexOption.IGNORE_CASE).containsMatchIn(n)) {
        parts.add("Altyazılı")
    }
    if (parts.isEmpty()) return "Standart"
    return parts.joinToString(" · ")
}
""")

print("Domain format logic generated.")
