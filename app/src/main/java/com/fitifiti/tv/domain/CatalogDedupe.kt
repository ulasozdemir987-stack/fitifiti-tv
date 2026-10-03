package com.fitifiti.tv.domain

import com.fitifiti.tv.data.xtream.Variant
import java.util.Locale

// Sağlayıcı aynı filmi/diziyi birden fazla kez veriyor ("Ucuz Roman" / "Ucuz Roman - Pulp Fiction (1994)" /
// "… [4K]"). (temizlenmiş başlık + yıl) için tek kayıt bırakılır; kopyalar "Sürüm" seçimi için variants'ta tutulur.
// Binlerce başlıkta çağrıldığı için kalıplar bir kez derlenir
private val YEAR_TAIL = Regex("\\s*[(\\[]\\s*(?:19|20)\\d{2}\\s*[)\\]]\\s*$")
private val BRACKETS = Regex("\\[[^\\]]*\\]")
private val DASH_TAIL = Regex("\\s+-\\s+.+$")
private val MARKS = Regex("\\p{M}+")
private val NON_ALNUM = Regex("[^a-z0-9]+")
private val TR_LOCALE = Locale("tr", "TR")
private val RE_4K = Regex("\\b(4k|uhd|2160p?)\\b", RegexOption.IGNORE_CASE)
private val RE_HDR = Regex("hdr|dolby|\\bdv\\b", RegexOption.IGNORE_CASE)
private val RE_DUB = Regex("tr\\s*dub|dublaj", RegexOption.IGNORE_CASE)
private val RE_SUB = Regex("tr\\s*sub|altyaz", RegexOption.IGNORE_CASE)

fun normalizeTitleKey(raw: String?): String {
    var s = raw.orEmpty()
    s = s.replace(YEAR_TAIL, "")
    s = s.replace(BRACKETS, " ")
    s = s.replace(DASH_TAIL, "")
    s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(MARKS, "")
    s = s.lowercase(TR_LOCALE).replace("ı", "i")
    return s.replace(NON_ALNUM, " ").trim()
}

fun variantLabel(name: String, categoryName: String = ""): String {
    val n = "$name $categoryName"
    val parts = mutableListOf<String>()
    if (RE_4K.containsMatchIn(n)) parts += if (RE_HDR.containsMatchIn(name)) "4K HDR" else "4K"
    if (RE_DUB.containsMatchIn(n)) parts += "Türkçe dublaj"
    else if (RE_SUB.containsMatchIn(n)) parts += "Altyazılı"
    return parts.joinToString(" · ")
}

class DedupeItem<T>(val item: T, val name: String, val year: String, val rating: Double, val variant: Variant?)

/** Puanı olan ve teknik etiket taşımayan (daha temiz) kopya tutulur. */
fun <T> dedupe(items: List<DedupeItem<T>>, withVariants: (T, List<Variant>) -> T): List<T> {
    fun richness(d: DedupeItem<T>) = (if (d.rating > 0) 2 else 0) + (if (d.name.contains('[') || d.name.contains(']')) 0 else 1)
    fun better(a: DedupeItem<T>, b: DedupeItem<T>): Boolean {
        val ra = richness(a); val rb = richness(b)
        return if (ra != rb) ra > rb else a.name.length < b.name.length
    }
    val kept = LinkedHashMap<String, DedupeItem<T>>()
    val groups = HashMap<String, MutableList<Variant>>()
    var unnamed = 0
    for (d in items) {
        val k = normalizeTitleKey(d.name).let { if (it.isEmpty()) "__u${unnamed++}" else "$it|${d.year}" }
        d.variant?.let { groups.getOrPut(k) { mutableListOf() }.add(it) }
        val cur = kept[k]
        if (cur == null || better(d, cur)) kept[k] = d
    }
    return kept.map { (k, d) ->
        val g = groups[k]
        if (g == null || g.size < 2) return@map d.item
        val byLabel = LinkedHashMap<String, Variant>()
        d.variant?.let { byLabel[it.label] = it }
        for (v in g) byLabel.putIfAbsent(v.label, v)
        if (byLabel.size < 2) return@map d.item
        var list = byLabel.values.toList()
        if (list.any { it.label.contains("dublaj") } && list.none { it.label.contains("Altyazılı") })
            list = list.map { if (it.label == "Standart") it.copy(label = "Orijinal dil") else it }
        withVariants(d.item, list)
    }
}
