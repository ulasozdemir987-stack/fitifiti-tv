package com.fitifiti.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.domain.cardTitle
import com.fitifiti.tv.ui.rememberFocus
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay

/**
 * Netflix / Prime Video TV tarzı yatay kart (16:9): içeriğin yazısız sahne görseli + üstünde logo ya da ad.
 * Görsel TMDB'den (anahtarsızsa site aracısı) kart ekrana gelince istenir; hızlı kaydırırken istek yağmasın diye kısa
 * bekleme var. Sahne görseli yoksa afiş bulanık zemin + sağda afişin kendisi (dikey afiş kırpılıp bozulmasın).
 */
@Composable
fun WideCard(
    item: Item, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 220.dp,
    progress: Float? = null, watched: Boolean = false, badge: String? = null, rank: Int? = null,
    onFocus: (() -> Unit)? = null, showText: Boolean = false,
) {
    val app = App.instance
    val key = item.key
    var backdrop by remember(key) { mutableStateOf(app.art.cached(item.kindKey, item.title, item.year, (item as? Item.M)?.m?.tmdb)?.backdrop) }
    var logo by remember(key) { mutableStateOf(app.art.cached(item.kindKey, item.title, item.year, (item as? Item.M)?.m?.tmdb)?.logo) }
    LaunchedEffect(key) {
        if (backdrop != null) return@LaunchedEffect
        delay(180)
        val a = when (item) {
            is Item.M -> app.art.art("movie", item.m.name, item.m.year, item.m.tmdb)
            is Item.S -> app.art.art("series", item.s.name, item.s.year)
        }
        backdrop = a.backdrop; logo = a.logo
    }
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        // Prime'daki "Top 10" gibi: kartın solunda büyük, içi boş görünen sıra numarası
        if (rank != null) Text("$rank", style = Display.copy(fontSize = 72.sp, color = Color(0x33FFFFFF), letterSpacing = (-4).sp),
            modifier = Modifier.padding(end = 2.dp).offset(y = 10.dp))
        Column(Modifier.width(width)) {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).rememberFocus().then(if (onFocus != null) Modifier.focusReport(onFocus = onFocus) else Modifier),
                shape = ClickableSurfaceDefaults.shape(WideShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = C.panel, focusedContainerColor = C.panel),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, com.fitifiti.tv.ui.components.RingBrush), shape = WideShape)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    val bd = backdrop
                    if (bd != null) AsyncImage(model = bd, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else if (!item.image.isNullOrBlank()) {
                        // (bulanık kopya kaldırıldı: kart başına bulanıklaştırma zayıf TV'lerde kaydırmayı takıltıyordu)
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1C1A2E), C.panel))))
                        AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize().padding(8.dp))
                    }
                    // alttan karartma: logo/ad okunur kalsın
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xCC000000))))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(if (bd == null && !item.image.isNullOrBlank()) 0.6f else 0.85f).padding(9.dp)) {
                        val lg = logo
                        if (lg != null && bd != null) AsyncImage(model = lg, contentDescription = item.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                            modifier = Modifier.fillMaxWidth(0.7f).heightIn(max = 32.dp))
                        else Text(cardTitle(item.title), style = Display.copy(fontSize = 14.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (watched) Row(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xBF000000)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = Color(0xFF6EE7B7)); Spacer(Modifier.width(3.dp))
                        Text("İzlendi", fontSize = 10.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.SemiBold)
                    }
                    if (badge != null) Text(badge, Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(C.primary).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (progress != null && progress > 0.01f && !watched) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 4.dp, track = Color(0x99000000))
                }
            }
            if (showText) {
                Spacer(Modifier.height(8.dp))
                MetaRow(listOf(item.year, com.fitifiti.tv.domain.formatGenres(item.genre, 1)), color = C.faint)
            }
        }
    }
}

private val Item.kindKey get() = if (this is Item.M) "movie" else "series"
private val WideShape = RoundedCornerShape(10.dp)
