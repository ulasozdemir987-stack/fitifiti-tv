package com.fitifiti.tv.domain

import java.util.Locale

// Sağlayıcının film/dizi kategori adlarını okunur yapar ("DiSNEY PLUS DiZiLERi" → "Disney+", "KORKU & PSiKOLOJiK" →
// "Korku & Psikolojik"). Sitedeki lib/category-style.ts ile aynı mantık.
data class CategoryStyle(val label: String, val color: Long, val platform: Boolean, val loc: String? = null, val logo: String? = null)

private data class Brand(val re: Regex, val label: String, val color: Long, val loc: String? = null, val logo: String? = null)
private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)
private val BRANDS = listOf(
    Brand(r("^netflix"), "Netflix", 0xFFE50914, "Netflix’te", "netflix"),
    Brand(r("^exxen"), "Exxen", 0xFFF5C518, "Exxen’de", "exxen"),
    Brand(r("^gain"), "Gain", 0xFFFF2D78, "Gain’de", "gain"),
    Brand(r("^amazon"), "Prime Video", 0xFF00A8E1, "Prime Video’da", "prime_video"),
    Brand(r("^disney"), "Disney+", 0xFF1F4FFF, "Disney+’ta", "disney_plus"),
    Brand(r("^tab[iı]{1,2}\\b"), "tabii", 0xFF00C2A8, "tabii’de", "tabii"),
    Brand(r("^hbo"), "HBO", 0xFF7B2FF7, "HBO’da", "hbo"),
    Brand(r("^max\\b.*blu"), "Max · BluTV", 0xFF0046FF, "BluTV’de", "blutv"),
    Brand(r("^b\\*+\\s*connect"), "TOD", 0xFFFEBC11, "TOD’da", "tod"),
    Brand(r("^apple"), "Apple TV+", 0xFFA3A3A3, "Apple TV+’ta", "appletv"),
    Brand(r("^bbc.*hulu"), "BBC · FOX · Hulu · CBS", 0xFF1CE783),
    Brand(r("^nb[sc].*paramount"), "NBC · The CW · Paramount", 0xFF0064FF),
    Brand(r("^g[uü]ncel\\s*tv"), "Güncel TV", 0xFFF97316, "Güncel TV’de"),
    Brand(r("^mubi"), "MUBI", 0xFFD4D4D4, "MUBI’de"),
    Brand(r("^marvel"), "Marvel", 0xFFEC1D24, null, "marvel"),
    Brand(r("^dc\\s"), "DC", 0xFF0476F2, null, "dc"),
    Brand(r("^imdb"), "IMDb Top 250", 0xFFF5C518, null, "imdb"),
    Brand(r("^kore"), "Kore", 0xFFE11D48),
    Brand(r("^anime"), "Anime", 0xFFF472B6),
)

private val WORDS = mapOf(
    "GUNCEL" to "Güncel", "ARSIV" to "Arşiv", "COCUK" to "Çocuk", "YERLI" to "Yerli", "YABANCI" to "Yabancı", "YETISKIN" to "Yetişkin",
    "ANIMASYON" to "Animasyon", "EGITIM" to "Eğitim", "SETLERI" to "Setleri", "DINI" to "Dini", "SUC" to "Suç", "SAVAS" to "Savaş",
    "KOMEDI" to "Komedi", "BIYOGRAFI" to "Biyografi", "ONERILERIMIZ" to "Önerilerimiz", "VIZYON" to "Vizyonda", "YESILCAM" to "Yeşilçam",
    "SIYAH" to "Siyah", "BEYAZ" to "Beyaz", "NOSTALJI" to "Nostalji", "POLISIYE" to "Polisiye", "PSIKOLOJIK" to "Psikolojik", "GIZEM" to "Gizem",
    "BILIM" to "Bilim", "KURGU" to "Kurgu", "AILE" to "Aile", "KLASIKLER" to "Klasikler", "SERI" to "Seri", "TIYATRO" to "Tiyatro",
    "ALTYAZILI" to "Altyazılı", "MACERA" to "Macera", "AKSIYON" to "Aksiyon", "FANTASTIK" to "Fantastik", "ROMANTIK" to "Romantik",
    "DRAM" to "Dram", "KORKU" to "Korku", "TARIH" to "Tarih", "BELGESEL" to "Belgesel", "DUBLAJ" to "Dublaj", "RELAX" to "Relax",
    "BLURAY" to "Blu-ray", "WORLD" to "World", "SPOR" to "Spor", "TALKSHOW" to "Talk show", "STAND-UP" to "Stand-up",
    "BOLLYWOOD" to "Bollywood", "WESTERN" to "Western", "KEMAL" to "Kemal", "SUNAL" to "Sunal", "UNIVERSE" to "Universe",
)
private val LANG = mapOf("DE" to "Almanca", "FR" to "Fransızca", "EN" to "İngilizce")
private val TR = Locale("tr", "TR")
private fun asciiKey(w: String) = w.uppercase(TR).replace('İ', 'I').replace('Ş', 'S').replace('Ç', 'C').replace('Ğ', 'G').replace('Ü', 'U').replace('Ö', 'O')

private fun word(w: String, foreign: Boolean): String {
    if (!Regex("\\p{L}").containsMatchIn(w)) return w
    if (foreign) return if (w == w.uppercase()) w.take(1) + w.drop(1).lowercase() else w
    if (Regex("^4K$", RegexOption.IGNORE_CASE).matches(w)) return "4K"
    if (Regex("^IMDb$", RegexOption.IGNORE_CASE).matches(w)) return "IMDb"
    val up = if (Regex("^[\\p{Lu}i]+$").matches(w)) w.replace('i', 'İ') else w
    WORDS[asciiKey(up)]?.let { return it }
    if (up != up.uppercase(TR)) return up
    val lower = up.lowercase(TR)
    return lower.take(1).uppercase(TR) + lower.drop(1)
}

private fun tidy(raw: String): String {
    var s = raw.replace(Regex("\\s+"), " ").trim()
    var lang = ""
    Regex("\\s*\\[(DE|FR|EN)]\\s*$", RegexOption.IGNORE_CASE).find(s)?.let { lang = LANG[it.groupValues[1].uppercase()] ?: ""; s = s.removeRange(it.range) }
    Regex("^(DE|FR|EN):\\s*", RegexOption.IGNORE_CASE).find(s)?.let { lang = LANG[it.groupValues[1].uppercase()] ?: ""; s = s.removeRange(it.range) }
    val stripped = s.replace(Regex("\\s+(D[İIi]Z[İIi](LER[İIi])?|F[İIi]LM(LER[İIi]?)?)$", RegexOption.IGNORE_CASE), "").trim()
    if (stripped.isNotEmpty()) s = stripped
    // ayraçları koruyarak böl
    val tokens = mutableListOf<String>(); var last = 0
    for (m in Regex("(\\s+|&|\\(|\\))").findAll(s)) { tokens += s.substring(last, m.range.first); tokens += m.value; last = m.range.last + 1 }
    tokens += s.substring(last)
    var label = tokens.joinToString("") { word(it, lang.isNotEmpty()) }.replace(Regex("\\s*&\\s*"), " & ").replace(Regex("\\s+"), " ").trim()
    if (lang.isNotEmpty()) label = "$label · $lang"
    return label
}

private val HUES = listOf(0xFF8B5CF6, 0xFF2DD4BF, 0xFF6366F1, 0xFFEC4899, 0xFFF59E0B, 0xFF22C55E, 0xFF0EA5E9, 0xFFF43F5E)
private fun hashColor(s: String): Long { var h = 0L; for (c in s) h = (h * 31 + c.code) and 0xFFFFFFFFL; return HUES[(h % HUES.size).toInt()] }

fun categoryStyle(rawName: String?): CategoryStyle {
    val name = rawName.orEmpty().trim()
    BRANDS.firstOrNull { it.re.containsMatchIn(name) }?.let { return CategoryStyle(it.label, it.color, true, it.loc, it.logo) }
    val label = tidy(name).ifEmpty { name }
    return CategoryStyle(label, hashColor(label), false)
}
fun categoryLabel(rawName: String?) = categoryStyle(rawName).label

private val FEATURED = listOf("^v[iİı]zyon", "^imdb", "aks[iİı]yon", "komed[iİı]", "^dram", "^a[iİı]le", "pol[iİı]s[iİı]ye", "korku", "b[iİı]l[iİı]m\\s*kurgu", "^yerl[iİı]\\s*f[iİı]lm").map { r(it) }
fun categoryRank(rawName: String?): Double {
    val n = rawName.orEmpty()
    val f = FEATURED.indexOfFirst { it.containsMatchIn(n) }
    if (f >= 0) return f.toDouble()
    if (r("^apple").containsMatchIn(n)) return 99.0
    if (r("^g[uü]ncel\\s*tv").containsMatchIn(n)) return 100.5
    if (r("\\[(DE|FR|EN)]|^(DE|FR|EN):").containsMatchIn(n)) return 300.0
    if (r("^4k\\b|dublaj|altyaz[iİı]l[iİı]").containsMatchIn(n)) return 200.0
    return 100.0
}
