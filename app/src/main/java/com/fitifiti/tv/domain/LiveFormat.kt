package com.fitifiti.tv.domain

import java.util.Locale

// Sık çağrılan işlevlerin kalıpları bir kez derlenir (TV işlemcisinde binlerce kez derlemek donmaya yol açıyordu)
private val RX0 = Regex("[ÇĞİÖŞÜçğıöşü]")
private val RX1 = Regex("(^|[\\s\\-/(.'’])(\\p{L})")
private val RX2 = Regex("\\b(Fk|Fc|Sk|Ac|Psg|Afc|Bk|Tff|Uefa|Nba|Bsl|Rb)\\b")
private val RX3 = Regex("\\s+")
private val RX4 = r("\\b(bant|banttan|tekrar)\\b")
private val RX5 = r("[-–—\\s]*\\b(bant|banttan|tekrar|canl[iı])\\b[-–—\\s]*$")
private val RX6 = Regex("[-–—\\s]+$")
private val RX7 = Regex("\\(\\d{2}\\s*[-/]\\s*\\d{2}\\)")
private val RX8 = r("(\\d+)\\s*\\.?\\s*(hafta|tur|ma[cç]|week)\\b")
private val RX9 = r("hafta|week")
private val RX10 = r("tur")
private val RX11 = Regex("^[\\s:·-]+")
private val RX12 = r("\\s+[-–—]\\s+|\\s+vs\\.?\\s+|\\s+v\\s+")
private val RX13 = Regex("\\s*\\([^)]*$")
private val RX14 = Regex("\\s*\\((\\d{4})\\)\\s*$")
private val RX15 = r("\\s*(raw|ᴿᴬᵂ)$")
private val RX16 = Regex("^[^\\p{L}\\p{N}]+")

// Yayın rehberi başlıkları: "SUPER LIG (26-27) 2. HAFTA TRABZONSPOR - BASAKSEHIR - BANT -" → Süper Lig · 2. Hafta, Trabzonspor – Başakşehir
private val TR = Locale("tr", "TR")
private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)
private val LEAGUES = listOf(
    r("^s[uü]per\\s*lig") to "Süper Lig", r("^tff\\s*1\\.?\\s*lig") to "TFF 1. Lig", r("^tff\\s*2\\.?\\s*lig") to "TFF 2. Lig",
    r("^premier\\s*league") to "Premier League", r("^la\\s*liga") to "LaLiga", r("^serie\\s*a") to "Serie A", r("^bundesliga") to "Bundesliga",
    r("^ligue\\s*1") to "Ligue 1", r("^(uefa\\s*)?[sş]ampiyonlar\\s*ligi|^champions\\s*league") to "Şampiyonlar Ligi",
    r("^(uefa\\s*)?avrupa\\s*ligi|^europa\\s*league") to "Avrupa Ligi", r("^(uefa\\s*)?konferans\\s*ligi|^conference\\s*league") to "Konferans Ligi",
    r("^t[uü]rkiye\\s*kupas[iı]|^ziraat") to "Türkiye Kupası", r("^euroleague") to "EuroLeague", r("^nba") to "NBA",
    r("^bsl|^basketbol\\s*s[uü]per\\s*ligi") to "Basketbol Süper Ligi", r("^eredivisie") to "Eredivisie", r("^liga\\s*portugal") to "Liga Portugal",
)
private val TEAMS = mapOf(
    "basaksehir" to "Başakşehir", "besiktas" to "Beşiktaş", "fenerbahce" to "Fenerbahçe", "galatasaray" to "Galatasaray", "trabzonspor" to "Trabzonspor",
    "kasimpasa" to "Kasımpaşa", "goztepe" to "Göztepe", "eyupspor" to "Eyüpspor", "caykur rizespor" to "Çaykur Rizespor", "rizespor" to "Rizespor",
    "genclerbirligi" to "Gençlerbirliği", "kocaelispor" to "Kocaelispor", "gaziantep fk" to "Gaziantep FK", "samsunspor" to "Samsunspor",
    "alanyaspor" to "Alanyaspor", "antalyaspor" to "Antalyaspor", "konyaspor" to "Konyaspor", "kayserispor" to "Kayserispor", "karagumruk" to "Karagümrük",
    "fatih karagumruk" to "Fatih Karagümrük", "erzurumspor" to "Erzurumspor", "sivasspor" to "Sivasspor", "hatayspor" to "Hatayspor", "bodrumspor" to "Bodrumspor",
    "manisa fk" to "Manisa FK", "ankaragucu" to "Ankaragücü", "pendikspor" to "Pendikspor", "istanbulspor" to "İstanbulspor", "corum fk" to "Çorum FK",
    "bandirmaspor" to "Bandırmaspor", "igdir fk" to "Iğdır FK", "boluspor" to "Boluspor", "sakaryaspor" to "Sakaryaspor", "umraniyespor" to "Ümraniyespor",
    "keciorengucu" to "Keçiörengücü", "amedspor" to "Amedspor", "adanaspor" to "Adanaspor",
)

fun titleCaseTr(s: String): String {
    val tr = RX0.containsMatchIn(s)
    val loc = if (tr) TR else Locale.US
    return s.lowercase(loc).replace(RX1) { it.groupValues[1] + it.groupValues[2].uppercase(loc) }
        .replace(RX2) { it.value.uppercase() }
}
private fun asciiKey(s: String) = s.lowercase(TR).replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ş', 's').replace('ü', 'u').replace(RX3, " ").trim()
private fun teamName(raw: String): String { val t = raw.replace(RX3, " ").trim(); return TEAMS[asciiKey(t)] ?: if (t == t.uppercase()) titleCaseTr(t) else t }

data class ParsedMatch(val league: String?, val round: String?, val home: String, val away: String, val tape: Boolean)

fun parseMatch(title: String): ParsedMatch? {
    var t = title.replace(RX3, " ").trim()
    val tape = RX4.containsMatchIn(t)
    t = t.replace(RX5, "").replace(RX6, "").trim()
    t = t.replace(RX7, " ").replace(RX3, " ").trim()
    var head = ""; var rest = t; var round: String? = null
    val rm = RX8.find(t)
    if (rm != null) {
        head = t.substring(0, rm.range.first).trim(); rest = t.substring(rm.range.last + 1).trim()
        val kind = rm.groupValues[2]
        round = "${rm.groupValues[1]}. " + when { RX9.containsMatchIn(kind) -> "Hafta"; RX10.containsMatchIn(kind) -> "Tur"; else -> "Maç" }
    } else for ((re, _) in LEAGUES) { val m = re.find(t); if (m != null) { head = m.value; rest = t.substring(m.range.last + 1).replace(RX11, ""); break } }
    val parts = rest.split(RX12).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val league = if (head.isNotEmpty()) LEAGUES.firstOrNull { it.first.containsMatchIn(head) }?.second ?: titleCaseTr(head.trimEnd(':', '·', '-', ' ')) else null
    return ParsedMatch(league, round, teamName(parts[0]), teamName(parts[1]), tape)
}

fun cleanProgrammeTitle(title: String): String {
    var t = title.replace(RX3, " ").trim().replace(RX13, "").trim().replace(RX14, "")
    if (t.length > 3 && t == t.uppercase()) t = titleCaseTr(t)
    return t
}
fun programmeHeadline(title: String): String = parseMatch(title)?.let { "${it.home} – ${it.away}" } ?: cleanProgrammeTitle(title)
fun cleanChannelName(name: String) = name.replace(RX15, "").replace(RX16, "").trim()
