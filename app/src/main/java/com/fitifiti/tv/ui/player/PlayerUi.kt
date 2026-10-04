package com.fitifiti.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.fitifiti.tv.ui.theme.C

/**
 * Oynatıcı düğmesi: çerçevesiz yuvarlak ikon, odakta beyaz zemin + siyah ikon (sitedeki CtrlBtn'in TV hali).
 * Netflix TV gibi adı yalnız odaktayken altında görünür (yer kaplamaz).
 */
@Composable
fun CtrlBtn(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, active: Boolean = false, showLabel: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Box(contentAlignment = Alignment.TopCenter) {
        Surface(
            onClick = onClick, modifier = modifier.size(size), interactionSource = src,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (active) C.primary.copy(alpha = 0.28f) else Color.Transparent, contentColor = if (active) Color(0xFFC4B5FD) else Color.White,
                focusedContainerColor = Color.White, focusedContentColor = Color.Black,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(size * 0.52f)) } }
        if (showLabel && focused) Text(
            label, Modifier.offset(y = size + 6.dp).wrapContentWidth(unbounded = true),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false,
        )
    }
}

/** Metinli oynatıcı düğmesi ("Girişi atla", "Sonraki bölüm") */
@Composable
fun PillBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = false) {
    Surface(
        onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.25f), 10.dp)),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (icon != null) { Spacer(Modifier.width(8.dp)); Icon(icon, null, Modifier.size(20.dp)) }
        }
    }
}

/** İlerleme çubuğu: mor → turkuaz geçiş çubuğun tamamına yayılır, dolan kısım soldan açılır; odakta kalınlaşır, tutamaç o noktanın rengini alır */
@Composable
fun SeekBar(fraction: Float, buffered: Float, focused: Boolean, modifier: Modifier = Modifier, bubble: String? = null) {
    val f = fraction.coerceIn(0f, 1f)
    val h = if (focused) 7.dp else 4.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color(0x2EFFFFFF)))
        Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).height(h).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)))
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).drawWithContent {
            clipRect(right = size.width * f) { this@drawWithContent.drawContent() }
        }.background(C.progress))
        if (focused || bubble != null) Box(Modifier.offset(x = (w * f) - 9.dp).size(18.dp).clip(CircleShape).background(Color.White).padding(3.dp).clip(CircleShape).background(progressColor(f)))
        if (bubble != null) Box(Modifier.offset(x = ((w * f) - 34.dp).coerceIn(0.dp, w - 68.dp), y = (-30).dp).width(68.dp), contentAlignment = Alignment.Center) { ScrubBubble(bubble) }
    }
}

/** Sağdan açılan çekmece (ses-altyazı, bölümler, uyku) */
@Composable
fun SidePanel(visible: Boolean, title: String, width: Dp = 420.dp, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + slideInHorizontally { it / 3 }, exit = fadeOut() + slideOutHorizontally { it / 3 }) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Transparent, 0.4f to Color(0x99000000), 1f to Color(0xE6000000))), contentAlignment = Alignment.CenterEnd) {
            Column(Modifier.fillMaxHeight().width(width).background(C.panel.copy(alpha = 0.96f)).padding(28.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** Çekmecedeki seçenek satırı: seçili = ✓ + beyaz */
@Composable
fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White, contentColor = if (selected) Color.White else Color(0xB3FFFFFF), focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selected) "✓" else "", Modifier.width(22.dp), fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(text, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (hint != null) Text(hint, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
            }
        }
    }
}
