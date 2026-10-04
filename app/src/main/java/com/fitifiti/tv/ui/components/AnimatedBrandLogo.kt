package com.fitifiti.tv.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.fitifiti.tv.ui.theme.Manrope
import android.os.SystemClock

private var appStartNanos = 0L

@Composable
fun AnimatedBrandLogo(width: Dp, modifier: Modifier = Modifier, compact: Boolean = false, live: Boolean = false, replayKey: Any? = Unit, waveKey: Any? = null, fixedTime: Float? = null) {
    if (appStartNanos == 0L) appStartNanos = SystemClock.elapsedRealtimeNanos()
    val measurer = rememberTextMeasurer()
    val glyphStyle = BRAND_GLYPH_STYLE
    val unit = remember { Density(1f, 1f) }
    val f = remember(measurer) { measurer.measure("f", glyphStyle, density = unit) }
    val tt = remember(measurer) { measurer.measure("t", glyphStyle, density = unit) }
    
    val targetColors = remember {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            h in 5..10 -> listOf(Color(0xFFFB923C), Color(0xFFF472B6), Color(0xFFFBBF24))
            h in 11..16 -> listOf(Color(0xFF2DD4BF), Color(0xFF22D3EE), Color(0xFFA78BFA))
            h in 17..20 -> listOf(Color(0xFFF472B6), Color(0xFFC084FC), Color(0xFFFB7185))
            else -> listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFA855F7))
        }
    }
    val c1 by androidx.compose.animation.animateColorAsState(targetColors[0], label = "c1")
    val c2 by androidx.compose.animation.animateColorAsState(targetColors[1], label = "c2")
    val c3 by androidx.compose.animation.animateColorAsState(targetColors[2], label = "c3")

    var time by remember { mutableFloatStateOf(0f) }
    var lastWaveTime by remember { mutableFloatStateOf(-10f) }
    LaunchedEffect(waveKey) { if (waveKey != null) lastWaveTime = time }
    if (fixedTime != null) time = fixedTime
    else LaunchedEffect(replayKey, live) {
        val start = if (live) appStartNanos else withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    
    val viewX = if (compact) 86f else 70f
    val viewY = if (compact) 4f else 0f
    val viewW = if (compact) 400f else 440f
    val viewH = if (compact) 198f else 210f

    Canvas(modifier.size(width, width * (viewH / viewW))) { 
        drawBrandLogo(time, f, tt, viewX, viewY, viewW, viewH, live, listOf(c1, c2, c3), lastWaveTime) 
    }
}

/** Logonun t anındaki karesi (çizim alanının genişliğine ölçeklenir) */
fun DrawScope.drawBrandLogo(t: Float, f: TextLayoutResult, tt: TextLayoutResult, viewX: Float, viewY: Float, viewW: Float, viewH: Float, live: Boolean, liveColors: List<Color>, lastWaveTime: Float = -10f) {
        val s = size.width / viewW
        withTransform({ scale(s, s, Offset.Zero); translate(-viewX, -viewY) }) {
            // Harfler (doğal birimde, yatayda SX ölçekli)
            withTransform({ translate(TEXT_X, 0f); scale(SX, 1f, Offset.Zero) }) {
                val bA = if (live && liveColors.size >= 2) Brush.verticalGradient(listOf(liveColors[0], liveColors[1])) else TXT_A
                val bB = if (live && liveColors.size >= 3) Brush.verticalGradient(liveColors) else TXT_B
                GLYPHS.forEachIndexed { i, g ->
                    val brush = if (g.half == 0) bA else bB
                    val tap = g.tap.maxOfOrNull { tapAmount(t, it) } ?: 0f
                    val w = if (g.ch == 'ı') 31f else if (g.ch == 'f') 42f else 46f
                    
                    var eqScale = 1f
                    if (live && g.ch == 'ı' && i < 6) { // first three 'i's (indices 1, 3, 5)
                        val idx = (i - 1) / 2 // 0, 1, 2
                        val delay = idx * 0.18f
                        val waveElapsed = (t - lastWaveTime - delay)
                        if (waveElapsed >= 0f && waveElapsed <= 1.05f) {
                            val p = waveElapsed / 1.05f
                            eqScale = when {
                                p < 0.25f -> 1f + (p / 0.25f) * 1.4f
                                p < 0.45f -> 2.4f - ((p - 0.25f) / 0.2f) * 1.4f
                                p < 0.65f -> 1f + ((p - 0.45f) / 0.2f) * 0.9f
                                else -> 1.9f - ((p - 0.65f) / 0.35f) * 0.9f
                            }
                        }
                    }
                    
                    withTransform({ scale(1f + 0.06f * tap, (1f - 0.18f * tap) * eqScale, Offset(g.x + w / 2, FLOOR_Y)) }) {
                        when (g.ch) {
                            'ı' -> drawRoundRect(brush, Offset(g.x + 7.5f, I_TOP), Size(16f, X_HEIGHT), CornerRadius(2.6f))
                            'f' -> drawText(f, brush, Offset(g.x, FLOOR_Y - f.firstBaseline))
                            else -> drawText(tt, brush, Offset(g.x, FLOOR_Y - tt.firstBaseline))
                        }
                    }
                }
            }
            // Kedi: f'ye konana kadar f'nin arkasında (yalnız f'nin solu ve tepesinin üstü görünür)
            val mx = MX.at(t)[0]
            val my = MY.at(t)[0]
            val draw: DrawScope.() -> Unit = {
                translate(mx, my) {
                    val trail = TRAIL.at(t)[0]
                    if (trail > 0.01f) drawLine(TRAIL_BRUSH, Offset(-8f, -3f), Offset(-44f, -3f), 2.6f, StrokeCap.Round, alpha = trail)
                    val p = POSE.at(t)
                    withTransform({ rotate(p[0], Offset.Zero); scale(p[1], p[2], Offset.Zero) }) {
                        withTransform({ scale(CAT_SCALE, CAT_SCALE, Offset.Zero); translate(-70f, -138f) }) { drawCat(t) }
                    }
                }
            }
            if (t < T_ON_F) clipPath(PEEK_CLIP) { draw() } else draw()
        }
}

/** "f" ve "t" harfleri (Manrope ExtraBold, sahne biriminde 104) */
val BRAND_GLYPH_STYLE = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 104.sp)

/** Girişin süresi (sn): açılış ekranı bu kadar bekleyip geçebilir */
const val BRAND_INTRO_SEC = 4.15f

// --- Sahne ölçüleri (sitedekiyle aynı) ---
private const val VIEW_X = 70f
private const val VIEW_Y = 0f
private const val VIEW_W = 440f
private const val VIEW_H = 210f
private const val FLOOR_Y = 176f
private const val TEXT_X = 120f
private const val SX = 360f / 300f
private const val X_HEIGHT = 57f
private const val I_TOP = FLOOR_Y - X_HEIGHT
private const val F_LEFT = TEXT_X + 3 * SX
private const val F_TOP = FLOOR_Y - 77
private const val HIDE_X = TEXT_X + 30 * SX
private const val START_X = TEXT_X + 22 * SX
private const val CAT_SCALE = 0.6f
private const val SIT_Y = I_TOP + (138 - 112) * CAT_SCALE
private fun iCenter(x: Float) = TEXT_X + (x + 15.5f) * SX

private class Glyph(val ch: Char, val x: Float, val half: Int, val tap: List<Float> = emptyList())

// Zaman çizelgesi (sn); P = f'nin tepesine konma anı
private const val P = 1.7f
private const val T_PEEK_OUT = 0.35f
private const val T_PEEK_HOLD = 0.95f
private const val T_PEEK_BACK = 1.1f
private const val T_CROUCH = 1.3f
private const val T_JUMP_PEAK = 1.52f
private const val T_ON_F = P
private const val T_S1 = P + 0.3f
private const val T_H1 = P + 0.5f
private const val T_S2 = P + 0.7f
private const val T_H2 = P + 0.9f
private const val T_S3 = P + 1.1f
private const val T_H3 = P + 1.3f
private const val T_S4 = P + 1.5f
private const val T_CROUCH2 = P + 1.75f
private const val T_HOP = P + 1.92f
private const val T_SIT = P + 2.1f
private const val T_SETTLE = P + 2.25f
private const val INTRO = P + 2.45f

private val GLYPHS = listOf(
    Glyph('f', 0f, 0, listOf(T_ON_F)), Glyph('ı', 42f, 0, listOf(T_S1)), Glyph('t', 73f, 0), Glyph('ı', 119f, 0, listOf(T_S2)),
    Glyph('f', 150f, 1), Glyph('ı', 192f, 1, listOf(T_S3)), Glyph('t', 223f, 1), Glyph('ı', 269f, 1, listOf(T_S4, T_SIT)),
)
private val STOPS = GLYPHS.filter { it.ch == 'ı' }.map { iCenter(it.x) }
private val HOPS = listOf(Triple(T_S1, T_H1, T_S2), Triple(T_S2, T_H2, T_S3), Triple(T_S3, T_H3, T_S4))

private val TXT_A = Brush.linearGradient(listOf(Color(0xFF67F6FF), Color(0xFF38BDF8)), Offset(0f, 100f), Offset(150f, 176f))
private val TXT_B = Brush.linearGradient(0f to Color(0xFFE879F9), 0.5f to Color(0xFFC084FC), 1f to Color(0xFFA855F7), start = Offset(150f, 100f), end = Offset(300f, 176f))
private val BODY = Brush.linearGradient(0f to Color(0xFF00F2FE), 0.4f to Color(0xFF38BDF8), 0.72f to Color(0xFF6366F1), 1f to Color(0xFFA855F7), start = Offset(20f, 20f), end = Offset(120f, 130f))
private val FACE = Brush.linearGradient(listOf(Color.White, Color(0xFFE0F2FE)), Offset(40f, 40f), Offset(100f, 90f))
private val EAR_IN = Brush.verticalGradient(listOf(Color(0xFFF472B6), Color(0xFFEC4899)))
private val TRAIL_BRUSH = Brush.linearGradient(listOf(Color(0xE600F2FE), Color(0x00A855F7)), Offset(-8f, 0f), Offset(-44f, 0f))
private val PEEK_CLIP = Path().apply { addRect(Rect(-1000f, -1000f, F_LEFT, 2000f)); addRect(Rect(-1000f, -1000f, 2000f, F_TOP)) }

// --- Anahtar kare motoru: her karenin eğrisi kendisinden sonraki parçaya uygulanır (CSS gibi) ---
private val EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val EASE_IN = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val UP = CubicBezierEasing(0.2f, 0.6f, 0.4f, 1f)
private val DOWN = CubicBezierEasing(0.6f, 0f, 0.8f, 0.4f)

private class K(val t: Float, val v: FloatArray, val e: Easing = EASE)
private class Track(kfs: List<K>) {
    private val k = kfs.sortedBy { it.t }
    fun at(t: Float): FloatArray {
        if (t <= k.first().t) return k.first().v
        for (i in 0 until k.size - 1) {
            val a = k[i]; val b = k[i + 1]
            if (t < b.t) {
                val f = if (b.t == a.t) 1f else a.e.transform((t - a.t) / (b.t - a.t))
                return FloatArray(a.v.size) { a.v[it] + (b.v[it] - a.v[it]) * f }
            }
        }
        return k.last().v
    }
}
private fun v(vararg x: Float) = x

private val MX = Track(buildList {
    add(K(0f, v(HIDE_X), CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)))
    add(K(T_PEEK_OUT, v(F_LEFT + 6), EASE_IN)); add(K(T_PEEK_HOLD, v(F_LEFT + 6), EASE_IN))
    add(K(T_PEEK_BACK, v(HIDE_X), EASE_OUT)); add(K(T_CROUCH, v(HIDE_X), EASE_OUT))
    add(K(T_ON_F, v(START_X), EASE_IN_OUT))
    add(K(T_S1, v(STOPS[0]), LinearEasing)); add(K(T_S2, v(STOPS[1]), LinearEasing)); add(K(T_S3, v(STOPS[2]), LinearEasing))
    add(K(T_S4, v(STOPS[3])))
})
private val MY = Track(buildList {
    add(K(0f, v(FLOOR_Y), UP)); add(K(T_CROUCH, v(FLOOR_Y), UP))
    add(K(T_JUMP_PEAK, v(F_TOP - 18), DOWN))
    add(K(T_ON_F, v(F_TOP), EASE_IN))
    HOPS.forEach { (a, h, _) -> add(K(a, v(I_TOP), UP)); add(K(h, v(I_TOP - if (h == T_H2) 36 else 32), DOWN)) }
    add(K(T_S4, v(I_TOP), UP)); add(K(T_CROUCH2, v(I_TOP), UP))
    add(K(T_HOP, v(I_TOP - 18), DOWN))
    add(K(T_SIT, v(SIT_Y), EASE_OUT))
    add(K(T_SETTLE, v(SIT_Y - 4), EASE_IN_OUT))
    add(K(INTRO, v(SIT_Y)))
})
/** Duruş: döndürme (derece), ölçek x, ölçek y — ayakların altına göre */
private val POSE = Track(buildList {
    fun p(t: Float, r: Float = 0f, sx: Float = 1f, sy: Float = 1f) = add(K(t, v(r, sx, sy)))
    p(0f); p(T_PEEK_OUT, -13f); p(0.6f, -9f); p(T_PEEK_HOLD, -12f); p(T_PEEK_BACK)
    p(T_CROUCH, 0f, 1.12f, 0.84f); p(T_CROUCH + 0.08f, 0f, 0.92f, 1.1f); p(T_ON_F - 0.02f); p(T_ON_F, 0f, 1.1f, 0.88f); p(T_ON_F + 0.08f)
    p(T_S1 - 0.05f, 7f)
    HOPS.forEach { (a, _, b) -> p(a, 3f, 1.08f, 0.9f); p(a + 0.07f, 6f, 0.95f, 1.06f); p(b - 0.03f, 6f) }
    p(T_S4, 0f, 1.1f, 0.88f); p(T_CROUCH2 - 0.05f, 0f, 1.12f, 0.84f); p(T_CROUCH2 + 0.08f, 0f, 0.93f, 1.08f)
    p(T_SIT, 0f, 1.1f, 0.88f); p(T_SETTLE, 0f, 0.97f, 1.03f); p(INTRO)
})
/** Bacakların toplanması: döndürme, ölçek y (bacakların üstüne göre) */
private val TUCK = Track(buildList {
    fun p(t: Float, r: Float = 0f, sy: Float = 1f) = add(K(t, v(r, sy)))
    p(0f); p(T_CROUCH + 0.05f); p(T_JUMP_PEAK, -16f, 0.9f); p(T_ON_F - 0.02f)
    HOPS.forEach { (a, h, b) -> p(a + 0.05f); p(h, -16f, 0.9f); p(b - 0.03f) }
    p(T_CROUCH2 + 0.05f); p(T_HOP, -12f, 0.92f); p(T_SIT - 0.03f); p(INTRO)
})
/** Yüzün bakışı (yola / kameraya) */
private val LOOK = Track(listOf(K(0f, v(-2f, 0f)), K(T_PEEK_HOLD, v(-2f, 0f)), K(T_CROUCH, v(6f, 1f)), K(T_SIT, v(6f, 1f)), K(T_SETTLE + 0.1f, v(0f, 0f))))
private val TRAIL = Track(listOf(K(0f, v(0f)), K(T_ON_F + 0.05f, v(0f)), K(T_ON_F + 0.2f, v(0.8f)), K(T_S4 - 0.1f, v(0.8f)), K(T_S4 + 0.05f, v(0f))))

/** Kedi o harfe konunca kısa esneme (0..1) */
private fun tapAmount(t: Float, at: Float): Float {
    val d = t - at
    if (d < 0f || d > 0.32f) return 0f
    return 1f - EASE_OUT.transform(d / 0.32f)
}
/** Döngüsel anahtar kareler: süre, gecikme, (oran, değer) */
private fun loop(t: Float, period: Float, delay: Float, vararg frames: Pair<Float, Float>, ease: Easing = EASE_IN_OUT): Float {
    if (t < delay) return frames.first().second
    val p = ((t - delay) % period) / period
    for (i in 0 until frames.size - 1) {
        val (a, va) = frames[i]; val (b, vb) = frames[i + 1]
        if (p <= b) return va + (vb - va) * ease.transform(if (b == a) 1f else (p - a) / (b - a))
    }
    return frames.last().second
}

// --- Kedi çizimi (140×140; tekerleklerin altı 70,138) ---
private fun path(d: String) = PathParser().parsePathString(d).toPath()
private val TAIL = path("M 46 104 C 30 110 14 98 18 82 C 22 68 8 60 12 48 C 14 42 22 46 22 52 C 22 66 32 78 40 90")
private val STAR = path("M 12 48 l1.6 -5 1.6 5 5 1.6 -5 1.6 -1.6 5 -1.6 -5 -5 -1.6 z")
private val BODY_P = path("M 48 82 C 42 90 44 108 52 112 C 60 114 80 114 88 112 C 96 108 98 90 92 82 Z")
private val CHEST = path("M 55 85 L 85 85 L 80 105 L 60 105 Z")
private val BADGE = path("M 66 89 L 78 95 L 66 101 Z")
private val EAR_L = path("M 38 46 L 24 16 C 23 14 26 13 28 15 L 54 36 Z")
private val EAR_L_IN = path("M 37 42 L 28 20 L 48 35 Z")
private val EAR_R = path("M 86 36 L 112 15 C 114 13 117 14 116 16 L 102 46 Z")
private val EAR_R_IN = path("M 92 35 L 112 20 L 103 42 Z")
private val HEAD = path("M 34 56 C 30 72 44 88 70 88 C 96 88 110 72 106 56 C 102 40 88 34 70 34 C 52 34 38 40 34 56 Z")
private val FACE_P = path("M 44 60 C 40 70 48 80 70 80 C 92 80 100 70 96 60 C 92 50 82 52 70 52 C 58 52 48 50 44 60 Z")
private val PHONES = path("M 30 52 C 30 30 46 22 70 22 C 94 22 110 30 110 52")
private val WINK = path("M 79 63 Q 86 56 93 63")
private val GLINT = path("M 0 -7 L 1.8 -1.8 L 7 0 L 1.8 1.8 L 0 7 L -1.8 1.8 L -7 0 L -1.8 -1.8 Z")
private val NOSE = path("M 68 69 L 72 69 L 70 72 Z")
private val MOUTH = path("M 64 72 Q 67 76.5 70 72.5 Q 73 76.5 76 72")
private val INK = Color(0xFF090A12)
private val CYAN = Color(0xFF00F2FE)

private fun DrawScope.drawCat(t: Float) {
    // Kuyruk + yıldız
    rotate(loop(t, 3.2f, 0f, 0f to -7f, 0.5f to 9f, 1f to -7f), Offset(46f, 110f)) {
        drawPath(TAIL, BODY, style = Stroke(8f, cap = StrokeCap.Round))
        val tw = loop(t, 1.8f, 0f, 0f to 0f, 0.5f to 1f, 1f to 0f)
        scale(0.7f + 0.45f * tw, Offset(12f, 48f)) { drawPath(STAR, Color.White, alpha = 0.35f + 0.65f * tw) }
    }
    // Bacaklar + patenler
    val tuck = TUCK.at(t)
    withTransform({ rotate(tuck[0], Offset(70f, 100f)); scale(1f, tuck[1], Offset(70f, 100f)) }) {
        val swingL = loop(t, 1.6f, INTRO, 0f to 0f, 0.5f to 1f, 1f to 0f)
        leg(60f, 7f + 10f * swingL, 1f - 0.16f * swingL)
        val rr = if (t < INTRO) -7f else loop(t, 1.6f, INTRO, 0f to -7f, 0.25f to -4f, 0.75f to -17f, 1f to -7f)
        val rs = if (t < INTRO) 0.9f else loop(t, 1.6f, INTRO, 0f to 0.9f, 0.25f to 1f, 0.75f to 0.84f, 1f to 0.9f)
        leg(80f, rr, rs)
    }
    // Gövde + göğüs rozeti
    drawPath(BODY_P, BODY)
    drawPath(CHEST, Color(0xFF050508))
    drawPath(CHEST, CYAN, style = Stroke(1.5f))
    scale(1f + 0.14f * loop(t, 1.6f, 0f, 0f to 0f, 0.5f to 1f, 1f to 0f), Offset(72f, 95f)) { drawPath(BADGE, Color(0xFFE0FBFF)) }
    // Kafa
    val head = loop(t, 3.2f, INTRO + 0.3f, 0f to 0f, 0.3f to -3f, 0.65f to 2.5f, 1f to 0f)
    rotate(head, Offset(70f, 80f)) {
        drawPath(EAR_L, BODY); drawPath(EAR_L_IN, EAR_IN)
        drawPath(EAR_R, BODY); drawPath(EAR_R_IN, EAR_IN)
        drawPath(HEAD, BODY)
        drawPath(FACE_P, FACE)
        drawPath(PHONES, Color(0xFF00F2FE), style = Stroke(4.5f, cap = StrokeCap.Round))
        listOf(22f, 108f).forEach { x ->
            drawRoundRect(Color(0xFF22D3EE), Offset(x, 44f), Size(10f, 20f), CornerRadius(5f))
            drawRoundRect(Color.White, Offset(x, 44f), Size(10f, 20f), CornerRadius(5f), style = Stroke(1f))
        }
        val look = LOOK.at(t)
        translate(look[0], look[1]) {
            // Göz kırpma: 4,8 sn'de bir sağ göz kapanır, yerine yay + parıltı
            val wp = if (t < INTRO + 0.1f) 0f else ((t - INTRO - 0.1f) % 4.8f) / 4.8f
            val closed = when { wp < 0.03f -> 0f; wp < 0.05f -> (wp - 0.03f) / 0.02f; wp < 0.09f -> 1f; wp < 0.12f -> 1f - (wp - 0.09f) / 0.03f; else -> 0f }
            eye(54f, 1f)
            eye(86f, 1f - 0.92f * closed)
            if (wp in 0.05f..0.09f) drawPath(WINK, INK, style = Stroke(3.4f, cap = StrokeCap.Round))
            val g = when { wp < 0.06f -> 0f; wp < 0.09f -> (wp - 0.06f) / 0.03f * 1.25f; wp < 0.16f -> 1.25f * (1f - (wp - 0.09f) / 0.07f); else -> 0f }
            if (g > 0f) withTransform({ translate(97f, 52f); rotate(((wp - 0.06f) / 0.10f).coerceIn(0f, 1f) * 90f, Offset.Zero); scale(g, g, Offset.Zero) }) {
                drawPath(GLINT, Color.White, alpha = (g / 1.25f).coerceIn(0f, 1f))
            }
            drawPath(NOSE, Color(0xFFEC4899))
            drawPath(MOUTH, INK, style = Stroke(1.8f, cap = StrokeCap.Round))
            drawOval(Color(0x66F43F5E), Offset(41f, 68f), Size(8f, 4f))
            drawOval(Color(0x66F43F5E), Offset(91f, 68f), Size(8f, 4f))
        }
    }
}

private fun DrawScope.eye(cx: Float, sy: Float) {
    scale(1f, sy, Offset(cx, 62f)) {
        drawOval(INK, Offset(cx - 6.5f, 53.5f), Size(13f, 17f))
        drawOval(Color(0xFF00E5FF), Offset(cx - 4.6f, 57.8f), Size(9.2f, 12.4f))
        drawCircle(Color.White, 2.5f, Offset(cx - 2f, 59f))
    }
}

private fun DrawScope.leg(x: Float, rot: Float, sy: Float) {
    withTransform({ rotate(rot, Offset(x, 100f)); scale(1f, sy, Offset(x, 100f)) }) {
        drawRoundRect(BODY, Offset(x - 5.5f, 100f), Size(11f, 22f), CornerRadius(5.5f))
        drawRoundRect(Color(0xFF0B0C16), Offset(x - 14f, 119f), Size(26f, 11f), CornerRadius(3f))
        drawRoundRect(CYAN, Offset(x - 14f, 119f), Size(26f, 11f), CornerRadius(3f), style = Stroke(1.3f))
        drawCircle(Color(0xFFA855F7), 4f, Offset(x - 7f, 134f))
        drawCircle(CYAN, 4f, Offset(x + 6f, 134f))
    }
}

