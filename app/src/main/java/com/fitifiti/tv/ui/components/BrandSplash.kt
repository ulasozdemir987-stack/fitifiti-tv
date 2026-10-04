package com.fitifiti.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay

/** Açılış: obsidian zemin, hafif mor/turkuaz ışıma, ortada kedili logo animasyonu; bitince ya da bir tuşa basınca geçer */
@Composable
fun BrandSplash(onDone: () -> Unit) {
    val f = remember { FocusRequester() }
    val done by rememberUpdatedState(onDone)
    // Başlatıcıdaki OK'in bırakılışı buraya düşüp açılışı hemen geçmesin: önce basış görülmeli
    var down by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { done() }
    LaunchedEffect(Unit) { runCatching { f.requestFocus() }; delay(((BRAND_INTRO_SEC + 0.7f) * 1000).toLong()); done() }
    Box(
        Modifier.fillMaxSize().background(C.bg)
            .background(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.10f), Color.Transparent), radius = 900f))
            .focusRequester(f).focusable()
            .onKeyEvent { if (it.type == KeyEventType.KeyDown) down = true else if (it.type == KeyEventType.KeyUp && down) done(); true },
        contentAlignment = Alignment.Center,
    ) { AnimatedBrandLogo(440.dp) }
}

/** Kart seçenekleri penceresi (sitedeki sağ tık / uzun basma menüsünün TV hali): kumandada OK'e basılı tut */
@Composable
fun OptionsDialog(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { f.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(420.dp).background(C.panel, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2)
            Spacer(Modifier.height(14.dp))
            options.forEachIndexed { i, (label, action) ->
                Surface(
                    onClick = { onDismiss(); action() },
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(f) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Color(0xD9FFFFFF), focusedContainerColor = Color.White, focusedContentColor = Color.Black),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) { Text(label, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
