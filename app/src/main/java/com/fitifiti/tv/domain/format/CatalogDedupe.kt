package com.fitifiti.tv.domain.format

import java.util.Locale

fun normalizeTitleKey(raw: Any?): String {
    var str = (raw?.toString() ?: "")
    str = str.replace(Regex("\\s*[([]\\s*(?:19|20)\\d{2}\\s*[)\\]]\\s*$"), "")
    str = str.replace(Regex("\\[[^\\]]*\\]"), " ")
    str = str.replace(Regex("\\s+-\\s+.+$"), "")
    return foldTr(str) // Reusing foldTr from Format.kt for simplicity
}

data class Variant(val id: Int, val ext: String? = null, val label: String)

fun variantLabel(name: String, categoryName: String = ""): String {
    val n = "$name $categoryName"
    val parts = mutableListOf<String>()
    if (Regex("\\b(4k|uhd|2160p?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(n)) {
        parts.add(if (Regex("hdr|dolby|\\bdv\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) "4K HDR" else "4K")
    }
    if (Regex("tr\\s*dub|dublaj", RegexOption.IGNORE_CASE).containsMatchIn(n)) {
        parts.add("Türkçe dublaj")
    } else if (Regex("tr\\s*sub|altyaz", RegexOption.IGNORE_CASE).containsMatchIn(n)) {
        parts.add("Altyazılı")
    }
    if (parts.isEmpty()) return "Standart"
    return parts.joinToString(" · ")
}
