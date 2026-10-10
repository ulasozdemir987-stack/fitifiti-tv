package com.fitifiti.tv.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Ortam rengi: görselin baskın canlı rengi (48 px'lik küçük kopyadan; doygun ve parlak piksellerin doygunluk ağırlıklı
 * ortalaması). Vitrin ışıması bu renge yumuşakça geçer → her içerik kendi renginde "nefes alır" (Apple TV'deki gibi).
 * Sonuç önbellekte; renk bulunamazsa sitenin moru.
 */
private val cache = ConcurrentHashMap<String, Color>()

@Composable
fun rememberAmbient(url: String?, fallback: Color = C.primary): Color {
    val ctx = LocalContext.current
    val target by produceState(url?.let { cache[it] } ?: fallback, url) {
        if (url == null) { value = fallback; return@produceState }
        cache[url]?.let { value = it; return@produceState }
        val req = ImageRequest.Builder(ctx).data(url).size(48).allowHardware(false).memoryCacheKey("ambient:$url").build()
        val bmp = ((Coil.imageLoader(ctx).execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        val c = bmp?.let { withContext(Dispatchers.Default) { dominant(it) } }
        if (c != null) { cache[url] = c; value = c } else value = fallback
    }
    return animateColorAsState(target, tween(900), label = "ambient").value
}

private fun dominant(b: Bitmap): Color? {
    val w = b.width; val h = b.height
    if (w == 0 || h == 0) return null
    val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
    val hsv = FloatArray(3)
    var x = 0.0; var y = 0.0; var sw = 0.0; var vs = 0.0
    for (p in px) {
        android.graphics.Color.colorToHSV(p, hsv)
        val s = hsv[1]; val v = hsv[2]
        if (s < 0.28f || v < 0.22f) continue
        val wgt = (s * s * v).toDouble()
        val r = Math.toRadians(hsv[0].toDouble())
        x += Math.cos(r) * wgt; y += Math.sin(r) * wgt; sw += wgt; vs += s * wgt
    }
    if (sw < px.size * 0.01) return null
    val hue = ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, (vs / sw).toFloat().coerceIn(0.55f, 0.85f), 0.85f)))
    // sitenin moruna biraz yaklaştır: tüm içerikler aynı aileden görünsün
    return lerp(c, C.primary, 0.18f)
}
