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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
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
 * Sol dikey menü (OwnTV düzeni): yuvarlak köşeli koyu hap; en üstte kedi, ortada ikonlar, altta profil. Seçili sekme
 * mor zeminli + sağında küçük nokta; odaktaki öğe beyaz zemin + sağa açılan ad etiketi. Üzerinde kısa süre durunca
 * sekme açılır (eski üst çubuk gibi).
 */
@Composable
fun NavRail(
    items: List<RailItem>,
    selected: String,
    onSelect: (String) -> Unit,
    selectedFocus: FocusRequester,
    modifier: Modifier = Modifier,
    profile: (@Composable () -> Unit)? = null,
    onProfile: () -> Unit = {},
) {
    Column(
        modifier.width(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(Color(0xCC0B0B12))
            .border(1.dp, C.line, RoundedCornerShape(25.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RailCat(Modifier.padding(bottom = 6.dp))
        items.forEach { it ->
            RailButton(it, it.key == selected, { onSelect(it.key) }, if (it.key == selected) Modifier.focusRequester(selectedFocus) else Modifier)
        }
        if (profile != null) {
            Box(Modifier.padding(vertical = 4.dp).width(22.dp).height(1.dp).background(C.line))
            RailProfile(profile, onProfile)
        }
    }
}

@Composable
private fun RailButton(item: RailItem, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, selected) { if (focused && !selected) { delay(320); onSelect() } }
    val bg by animateColorAsState(when { focused -> Color.White; selected -> C.primary.copy(alpha = 0.28f); else -> Color.Transparent }, tween(160), label = "rail")
    Box(contentAlignment = Alignment.CenterStart) {
        Surface(
            onClick = onSelect, modifier = modifier.size(38.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = bg, contentColor = if (selected) Color.White else C.muted, focusedContentColor = Color.Black),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(item.icon, item.label, Modifier.size(19.dp)) } }
        if (selected && !focused) Box(Modifier.offset(x = 41.dp).size(4.dp).clip(CircleShape).background(C.teal))
        // ad etiketi: odaktayken menünün sağına açılır (ölçüye katılmaz → menü genişlemez)
        Box(Modifier.layout { m, c -> val p = m.measure(c.copy(minWidth = 0, maxWidth = 400.dp.roundToPx())); layout(0, 0) { p.place(48.dp.roundToPx(), -p.height / 2) } }) {
            AnimatedVisibility(focused, enter = fadeIn(tween(140)) + slideInHorizontally(tween(160)) { -it / 4 }, exit = fadeOut(tween(100)) + slideOutHorizontally { -it / 4 }) {
                Text(item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xF0181824)).border(1.dp, C.line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun RailProfile(content: @Composable () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.size(34.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = CircleShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) { Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) { content() } }
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
