package com.fitifiti.tv.ui.components

import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display

/** "fıtıfıtı" logosu: ilk yarı turkuaz, ikinci yarı mor (sitedeki marka) */
@Composable
fun Wordmark(size: Int = 26, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(brush = Brush.linearGradient(listOf(C.cyan, C.teal)))) { append("fıtı") }
            withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Color(0xFFA78BFA), C.primary)))) { append("fıtı") }
        },
        style = Display.copy(fontSize = size.sp, letterSpacing = (-0.02f * size).sp),
        modifier = modifier,
    )
}

/** Sitedeki ilerleme çizgisi: mor → turkuaz geçiş, dolan kısım soldan açılır */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, track: Color = Color(0x40FFFFFF)) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(C.progress))
    }
}

@Composable
fun Dot() = Text("·", color = C.faint, modifier = Modifier.padding(horizontal = 8.dp))

/** Yıl · süre · tür gibi meta satırı */
@Composable
fun MetaRow(parts: List<String?>, modifier: Modifier = Modifier, color: Color = C.muted) {
    val list = parts.filterNot { it.isNullOrBlank() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        list.forEachIndexed { i, p -> if (i > 0) Dot(); Text(p!!, color = color, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
    }
}

enum class BtnKind { Primary, Secondary, Ghost }

/** Sitedeki Btn: birincil beyaz, ikincil yarı saydam. Odakta hafif büyür + beyaz halka. */
@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Primary, icon: ImageVector? = null, enabled: Boolean = true) {
    val (bg, fg) = when (kind) {
        BtnKind.Primary -> Color.White to Color.Black
        BtnKind.Secondary -> C.fill3 to Color.White
        BtnKind.Ghost -> Color.Transparent to Color.White
    }
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.rememberFocus(),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(containerColor = bg, contentColor = fg, focusedContainerColor = if (kind == BtnKind.Primary) Color.White else Color(0x33FFFFFF), focusedContentColor = fg),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        border = ButtonDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, if (kind == BtnKind.Primary) C.primary else Color.White), shape = RoundedCornerShape(50))),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Seçim çipi (sürüm, sezon, sıralama): seçili = beyaz zemin */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier.rememberFocus(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else C.fill2, contentColor = if (selected) Color.Black else Color(0xD9FFFFFF),
            focusedContainerColor = if (selected) Color.White else Color(0x33FFFFFF), focusedContentColor = if (selected) Color.Black else Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(50))),
    ) { Text(text, Modifier.padding(horizontal = 16.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = modifier.padding(start = 48.dp, bottom = 12.dp))

/** Yükleniyor görünümü: yavaşça parıldayan kutu (sitedeki .skeleton) */
@Composable
fun Skeleton(modifier: Modifier, corner: Dp = 10.dp) {
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.04f, 0.10f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color.White.copy(alpha = a)))
}

/** Profil avatarı: renkli kare + baş harf (avatar = renk sırası) */
val AVATAR_COLORS = listOf(
    listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)), listOf(Color(0xFF2DD4BF), Color(0xFF0EA5E9)), listOf(Color(0xFFEC4899), Color(0xFFF43F5E)),
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), listOf(Color(0xFF22C55E), Color(0xFF14B8A6)), listOf(Color(0xFF6366F1), Color(0xFF0EA5E9)),
    listOf(Color(0xFFF472B6), Color(0xFFA855F7)), listOf(Color(0xFF64748B), Color(0xFF334155)),
)

@Composable
fun Avatar(name: String, avatar: Int, size: Dp, modifier: Modifier = Modifier) {
    val c = AVATAR_COLORS[(avatar % AVATAR_COLORS.size + AVATAR_COLORS.size) % AVATAR_COLORS.size]
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.18f)).background(Brush.linearGradient(c)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase(java.util.Locale("tr", "TR")), style = Display.copy(fontSize = (size.value * 0.42f).sp), color = Color.White)
    }
}

/** Boş/hata ekranı */
@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MascotOops()
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
        if (hint != null) { Spacer(Modifier.height(6.dp)); Text(hint, style = MaterialTheme.typography.bodyMedium, color = C.muted) }
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}
