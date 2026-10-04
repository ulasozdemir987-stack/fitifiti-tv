package com.fitifiti.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fitifiti.tv.data.tmdb.AwardCount
import com.fitifiti.tv.data.tmdb.Critics
import com.fitifiti.tv.ui.theme.Manrope
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sitedeki ödül defneleri (components/app/award-laurels.tsx): Oscar, Emmy, Altın Küre, BAFTA, Cannes, Venedik, Berlin,
 * Altın Portakal. Kazanılanlar altın, yalnız aday olunan Oscar/Emmy gümüş; en fazla 4. Sayı OMDb özeti (`critics.major`)
 * ile Wikidata ayrıntısının (`/api/awards`) büyüğü. Detay sayfasında puan satırının altında, sol blokta (fragmanın
 * üstüne binmez); sırayla belirir.
 */
val AWARD_FAMILIES = listOf("Oscar", "Emmy", "Altın Küre", "BAFTA", "Cannes", "Venedik", "Berlin", "Altın Portakal")
private val NOMINEE_OK = setOf("Oscar", "Emmy")

fun laurelBadges(c: Critics?, wiki: List<AwardCount>): List<AwardCount> {
    if (c == null) return emptyList()
    return AWARD_FAMILIES.map { f ->
        val o = c.major.firstOrNull { it.family == f }
        val w = wiki.firstOrNull { it.name() == f }
        AwardCount(f, maxOf(o?.wins ?: 0, w?.wins ?: 0), maxOf(o?.noms ?: 0, w?.noms ?: 0))
    }.filter { it.wins > 0 || (it.noms > 0 && it.family in NOMINEE_OK) }
        .sortedByDescending { it.wins > 0 }
        .take(4)
}
private fun AwardCount.name() = family

@Composable
fun AwardLaurels(badges: List<AwardCount>, modifier: Modifier = Modifier, height: Dp = 46.dp) {
    if (badges.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        badges.forEachIndexed { i, b -> Laurel(b, i * 120L, height) }
    }
}

private val GOLD = listOf(Color(0xFFF7E3A3), Color(0xFFD9AE52), Color(0xFFA87B2C))
private val SILVER = listOf(Color(0xFFEEF1F5), Color(0xFFB8C0CC), Color(0xFF8A93A3))

@Composable
private fun Laurel(b: AwardCount, delayMs: Long, height: Dp) {
    val won = b.wins > 0
    val grad = if (won) GOLD else SILVER
    val sub = if (won) (if (b.wins > 1) "${b.wins} ödül" else "Kazandı") else if (b.noms > 1) "${b.noms} adaylık" else "Aday"
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs); a.animateTo(1f, tween(420)) }
    Row(Modifier.height(height).graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 10f; val s = 0.92f + 0.08f * a.value; scaleX = s; scaleY = s },
        verticalAlignment = Alignment.CenterVertically) {
        Branch(grad, flip = false, height = height)
        Column(Modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(b.family, style = TextStyle(brush = Brush.verticalGradient(grad), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = (-0.3).sp))
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (won) Color(0xFFE7CF8F) else Color(0xFFC3CAD4))
        }
        Branch(grad, flip = true, height = height)
    }
}

/** Tek defne dalı: yay boyunca 7 çift yaprak + uçta bir yaprak (sitedeki SVG ile aynı geometri, viewBox 4 2 30 64) */
@Composable
private fun Branch(grad: List<Color>, flip: Boolean, height: Dp) {
    Canvas(Modifier.size(height * (30f / 64f), height)) {
        val k = size.height / 64f
        val brush = Brush.verticalGradient(grad, startY = 2f, endY = 66f)
        scale(if (flip) -1f else 1f, 1f, Offset(size.width / 2, size.height / 2)) {
            scale(k, k, Offset.Zero) {
                translate(-4f, -2f) {
                    val cx = 40f; val cy = 34f; val r = 29f
                    val stem = Path().apply {
                        arcTo(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r), 122f, (232f - 122f), true)
                    }
                    drawPath(stem, brush, style = Stroke(1.1f, cap = StrokeCap.Round))
                    val n = 7
                    for (i in 0 until n) {
                        val deg = 122.0 + i * (228.0 - 122.0) / (n - 1)
                        val t = Math.toRadians(deg)
                        val px = cx + r * cos(t).toFloat(); val py = cy + r * sin(t).toFloat()
                        val tang = Math.toDegrees(atan2(cos(t), -sin(t))).toFloat()
                        for (side in listOf(-1, 1)) {
                            val ang = tang + side * 38
                            val rad = Math.toRadians(ang.toDouble())
                            val sz = 1 - i * 0.045f
                            val lx = px + cos(rad).toFloat() * 3.6f * sz; val ly = py + sin(rad).toFloat() * 3.6f * sz
                            val rx = 4.3f - i * 0.18f
                            rotate(ang, Offset(lx, ly)) { drawOval(brush, Offset(lx - rx, ly - 1.65f), Size(rx * 2, 3.3f)) }
                        }
                    }
                    val tip = Math.toRadians(236.0)
                    val tx = cx + (r + 2) * cos(tip).toFloat(); val ty = cy + (r + 2) * sin(tip).toFloat()
                    val ta = Math.toDegrees(atan2(cos(tip), -sin(tip))).toFloat()
                    rotate(ta, Offset(tx, ty)) { drawOval(brush, Offset(tx - 3.6f, ty - 1.5f), Size(7.2f, 3f)) }
                }
            }
        }
    }
}

/** Ödül metninden defnelerde zaten görünen tören parçalarını at ("2 Oscar kazandı · 120 ödül, 247 adaylık" → "120 ödül, 247 adaylık") */
fun awardsRemainder(awards: String?, badges: List<AwardCount>): String? {
    if (awards.isNullOrBlank() || badges.isEmpty()) return awards
    val shown = badges.map { it.family }
    return awards.split(" · ").filter { part -> shown.none { part.contains(it, ignoreCase = true) } }.joinToString(" · ").ifBlank { null }
}
