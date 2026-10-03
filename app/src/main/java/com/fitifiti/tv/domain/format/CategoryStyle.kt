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
    if (Regex("^tab[iı]{1,2}\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("tabii", "#00c2a8", true)
    if (Regex("^hbo", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("HBO", "#7b2ff7", true)
    if (Regex("^max\\b.*blu", RegexOption.IGNORE_CASE).containsMatchIn(name)) return CategoryStyle("Max · BluTV", "#0046ff", true)
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
        Regex("b[iİı]l[iİı]m\\s*kurgu", RegexOption.IGNORE_CASE),
        Regex("^yerl[iİı]\\s*f[iİı]lm", RegexOption.IGNORE_CASE)
    )
    
    val fIdx = featured.indexOfFirst { it.containsMatchIn(n) }
    if (fIdx >= 0) return fIdx
    
    if (Regex("^apple", RegexOption.IGNORE_CASE).containsMatchIn(n)) return 99
    if (Regex("^4k\\b|dublaj|altyaz[iİı]l[iİı]", RegexOption.IGNORE_CASE).containsMatchIn(n)) return 200
    
    return 100
}
