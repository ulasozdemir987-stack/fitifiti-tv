package com.fitifiti.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Manrope
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sol menünün kapladığı genişlik: içerik bu kadar sağdan başlar (vitrin görselleri ise arkasına taşar, bkz. [bleedStart]) */
val RailInset: Dp = 38.dp

/** Arka plan katmanını içerik boşluğunun soluna, ekran kenarına kadar uzatır (sol menünün arkasına) */
fun Modifier.bleedStart(inset: Dp = RailInset): Modifier = layout { m, c ->
    val px = inset.roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + px, maxWidth = if (c.hasBoundedWidth) c.maxWidth + px else c.maxWidth))
    layout(p.width - px, p.height) { p.place(-px, 0) }
}

data class RailItem(val key: String, val icon: ImageVector, val label: String)

/**
 * Sol dikey menü (OwnTV düzeni): yuvarlak köşeli koyu hap; en üstte kedi, ortada ikonlar, altta profil. Odak menüye
 * gelince adlarıyla birlikte genişler (Google TV gibi; arkadaki içerik [onExpandedChange] ile karartılır), çıkınca
 * yine ince hap. Seçili sekme mor zemin + turkuaz nokta; odaktaki öğe beyaz. Üzerinde kısa süre durunca sekme açılır.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NavRail(
    items: List<RailItem>,
    selected: String,
    onSelect: (String) -> Unit,
    selectedFocus: FocusRequester,
    modifier: Modifier = Modifier,
    profile: (@Composable () -> Unit)? = null,
    profileName: String? = null,
    onProfile: () -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val t by androidx.compose.animation.core.animateFloatAsState(if (expanded) 1f else 0f, tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "rail")
    val width = 50.dp + (196.dp - 50.dp) * t
    Column(
        modifier.width(width)
            .onFocusChanged { if (it.hasFocus != expanded) { expanded = it.hasFocus; onExpandedChange(it.hasFocus) } }
            // cam hap (Glass.kt); açılınca koyu renk biraz artar ki yazılar okunsun
            .glass(RoundedCornerShape(25.dp), strength = 0.9f, tint = androidx.compose.ui.graphics.lerp(Color(0x1F0B0B12), Color(0xB30E0E17), t))
            .clip(RoundedCornerShape(25.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.padding(start = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RailCat(Modifier)
            if (t > 0.05f) Text("fıtıfıtı", maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp).graphicsLayer { alpha = t },
                style = TextStyle(brush = Brush.horizontalGradient(listOf(C.cyan, C.primary)), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp))
        }
        items.forEach { it ->
            RailButton(it, it.key == selected, t, { onSelect(it.key) }, if (it.key == selected) Modifier.focusRequester(selectedFocus) else Modifier)
        }
        if (profile != null) {
            Box(Modifier.padding(vertical = 4.dp, horizontal = 8.dp).width(22.dp + 120.dp * t).height(1.dp).background(C.line))
            RailProfile(profile, profileName, t, onProfile)
        }
    }
}

@Composable
private fun RailButton(item: RailItem, selected: Boolean, t: Float, onSelect: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, selected) { if (focused && !selected) { delay(320); onSelect() } }
    val bg by animateColorAsState(when { focused -> Color.White; selected -> C.primary.copy(alpha = 0.28f); else -> Color.Transparent }, tween(160), label = "rail")
    Box(contentAlignment = Alignment.CenterStart) {
        Surface(
            onClick = onSelect, modifier = modifier.height(38.dp).fillMaxWidth().onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg, contentColor = if (selected) Color.White else C.muted, focusedContentColor = Color.Black),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(item.icon, item.label, Modifier.size(19.dp)) }
                if (t > 0.05f) Text(item.label, fontSize = 14.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(start = 4.dp).graphicsLayer { alpha = t; translationX = (1 - t) * -12f })
            }
        }
        if (selected && !focused && t < 0.5f) Box(Modifier.offset(x = 41.dp).size(4.dp).clip(CircleShape).background(C.teal).graphicsLayer { alpha = 1 - t * 2 })
    }
}

@Composable
private fun RailProfile(content: @Composable () -> Unit, name: String?, t: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.height(38.dp).fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(19.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color(0x26FFFFFF), contentColor = Color.White, focusedContentColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape), contentAlignment = Alignment.Center) { content() }
            if (t > 0.05f && name != null) Column(Modifier.padding(start = 10.dp).graphicsLayer { alpha = t }) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Text("Profil değiştir", fontSize = 10.sp, color = C.muted, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Menünün tepesindeki marka: logodaki kedi (son kare, oturmuş) */
@Composable
private fun RailCat(modifier: Modifier) {
    Box(modifier.size(34.dp).drawBehind {
        val k = size.width / 140f
        drawCircle(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.35f), Color.Transparent), radius = size.width * 0.7f))
        translate(0f, size.height * 0.04f) { scale(k, k, Offset.Zero) { drawBrandCat(9f) } }
    })
}

/** Sağ üst köşe: büyük saat (mor → turkuaz) + tarih (OwnTV düzeni) */
@Composable
fun CornerClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000 - System.currentTimeMillis() % 1000 + 5); now = System.currentTimeMillis() } }
    val tr = remember { Locale("tr", "TR") }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(SimpleDateFormat("HH:mm", tr).format(Date(now)),
            style = TextStyle(brush = Brush.horizontalGradient(listOf(C.primary, C.teal)), fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp))
        Text(SimpleDateFormat("d MMMM EEE", tr).format(Date(now)), fontSize = 12.sp, color = C.muted)
    }
}

/** Sol üst köşede hafif mor ışıma — yalnız kendi çevresine çizilir (tam ekran radyal dolgu zayıf TV'de pahalı) */
fun Modifier.cornerGlow(radius: Float = 900f): Modifier = this.drawBehind {
    drawRect(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.16f), Color.Transparent), center = Offset.Zero, radius = radius),
        size = androidx.compose.ui.geometry.Size(minOf(radius, size.width), minOf(radius, size.height)))
}
