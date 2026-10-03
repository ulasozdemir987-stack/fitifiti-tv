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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.fitifiti.tv.ui.theme.C

/** Oynatıcı düğmesi: çerçevesiz yuvarlak ikon, odakta beyaz zemin + siyah ikon (sitedeki CtrlBtn'in TV hali) */
@Composable
fun CtrlBtn(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, active: Boolean = false) {
    Surface(
        onClick = onClick, modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color(0x33FFFFFF) else Color.Transparent, contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(size * 0.5f)) } }
}

/** Metinli oynatıcı düğmesi ("Girişi atla", "Sonraki bölüm") */
@Composable
fun PillBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = false) {
    Surface(
        onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color(0xB3121218), contentColor = if (primary) Color.Black else Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)), shape = RoundedCornerShape(50)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, C.primary), shape = RoundedCornerShape(50)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (icon != null) { Spacer(Modifier.width(8.dp)); Icon(icon, null, Modifier.size(20.dp)) }
        }
    }
}

/** İlerleme çubuğu: mor → turkuaz geçiş, odakta kalınlaşır, tutamaç belirir */
@Composable
fun SeekBar(fraction: Float, buffered: Float, focused: Boolean, modifier: Modifier = Modifier) {
    val h = if (focused) 8.dp else 4.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)))
        Box(Modifier.fillMaxWidth(buffered.coerceIn(0f, 1f)).height(h).clip(RoundedCornerShape(50)).background(Color(0x40FFFFFF)))
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(h).clip(RoundedCornerShape(50)).background(C.progress))
        if (focused) Box(Modifier.offset(x = (w * fraction.coerceIn(0f, 1f)) - 9.dp).size(18.dp).clip(CircleShape).background(Color.White))
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
