package com.fitifiti.tv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitifiti.tv.ui.theme.C

/** Sitedeki neon kedi maskotu (components/mascot-logo.tsx → assets/mascot.svg) */
@Composable
fun Mascot(size: Dp, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    AsyncImage(model = "file:///android_asset/mascot.svg", contentDescription = null, modifier = modifier.size(size), colorFilter = colorFilter)
}

/** Yükleniyor (sitedeki MascotLoader): maskot hafifçe zıplar, altında gölgesi nefes alır */
@Composable
fun MascotLoader(label: String? = null, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bob")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(size, Modifier.graphicsLayer {
            translationY = -p * size.toPx() * 0.16f
            // yere değerken hafif basılır
            scaleY = 1f - (1f - p) * 0.05f; scaleX = 1f + (1f - p) * 0.03f
            transformOrigin = TransformOrigin(0.5f, 1f)
        })
        Box(Modifier.padding(top = 4.dp).width(size * 0.45f).height(6.dp)
            .graphicsLayer { scaleX = 1f - p * 0.35f; alpha = 0.7f - p * 0.35f }
            .blur(2.dp).clip(CircleShape).background(Color.Black))
        if (label != null) { Spacer(Modifier.height(14.dp)); Text(label, style = MaterialTheme.typography.bodyLarge, color = C.muted, textAlign = TextAlign.Center) }
    }
}

/** Boş/hata (sitedeki MascotOops): başını hafif eğmiş, biraz soluk maskot */
@Composable
fun MascotOops(size: Dp = 84.dp, modifier: Modifier = Modifier) {
    val gray = ColorMatrix().apply { setToSaturation(0.65f) }
    Mascot(size, modifier.graphicsLayer { rotationZ = -10f; alpha = 0.9f }, colorFilter = ColorFilter.colorMatrix(gray))
}

/** Belirsiz ilerleme çizgisi: mor → turkuaz parça soldan sağa kayar (sitedeki oynatıcı açılışı) */
@Composable
fun IndeterminateLine(modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val t = rememberInfiniteTransition(label = "ind")
    val x by t.animateFloat(-0.4f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing)), label = "x")
    BoxWithConstraints(modifier.height(height).clip(CircleShape).background(C.fill3)) {
        Box(Modifier.fillMaxHeight().width(maxWidth * 0.4f).graphicsLayer { translationX = x * constraints.maxWidth }.clip(CircleShape).background(C.progress))
    }
}

/** Arkada yavaşça yakınlaşan karartılmış görsel (sitedeki Ken Burns) */
@Composable
fun KenBurns(image: String, alpha: Float) {
    val t = rememberInfiniteTransition(label = "kb")
    val s by t.animateFloat(1.04f, 1.14f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    AsyncImage(model = image, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = alpha,
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s })
}

/** Maskot + "fıtıfıtı" yazısı yan yana (sitedeki menü logosu gibi; kedi yazının solunda) */
@Composable
fun BrandLogo(size: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Mascot((size * 1.45f).dp)
        Spacer(Modifier.width((size * 0.2f).dp))
        Wordmark(size)
    }
}
