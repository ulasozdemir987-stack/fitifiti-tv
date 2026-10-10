package com.fitifiti.tv.ui.components

import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series
import com.fitifiti.tv.domain.cardTitle
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay

/** Afiş şeritlerinin ortak öğesi */
sealed interface Item {
    val key: String; val title: String; val image: String?; val year: String?; val genre: String?
    data class M(val m: Movie) : Item { override val key = "movie-${m.id}"; override val title = m.name; override val image = m.icon; override val year = m.year; override val genre = m.genre }
    data class S(val s: Series) : Item { override val key = "series-${s.id}"; override val title = s.name; override val image = s.cover; override val year = s.year; override val genre = s.genre }
}
fun Movie.item() = Item.M(this)
fun Series.item() = Item.S(this)

private val CardShape = RoundedCornerShape(10.dp)

val RingBrush = Brush.linearGradient(listOf(C.primary, C.teal))

/** Odak çerçevesi: sitenin mor → turkuaz geçişi (OwnTV'deki renkli odak çizgisinin bizim tonu) */
val FocusRing: ClickableSurfaceBorder
    @Composable get() = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, RingBrush), shape = CardShape))

/** Afişin sol üstündeki puan: "★ 7.5" */
@Composable
fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xCC0B0B12)).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("★", fontSize = 10.sp, color = Color(0xFFF5C451))
        Spacer(Modifier.width(3.dp))
        Text("%.1f".format(rating), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/** Odağa gelince bir süre sonra bildirir (vitrin arka planı bunu dinler) */
@Composable
fun Modifier.focusReport(delayMs: Long = 600, onFocus: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) { delay(delayMs); onFocus() } }
    return this.onFocusChanged { focused = it.hasFocus || it.isFocused }
}

/** Afiş kartı: 2:3 görsel; odakta büyür + beyaz çerçeve (TV'de odak görünmek zorunda), altta ad + yıl · tür */
@Composable
fun PosterCard(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 150.dp,
               progress: Float? = null, watched: Boolean = false, badge: String? = null, rank: Int? = null, onFocus: (() -> Unit)? = null, showText: Boolean = true,
               rating: Double? = null, onLongClick: (() -> Unit)? = null, focusDelayMs: Long = 600, showYear: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (rank != null) Text("$rank", style = Display.copy(fontSize = 84.sp, color = Color(0x26FFFFFF)), modifier = Modifier.padding(end = 2.dp))
        Column(if (width == androidx.compose.ui.unit.Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width)) {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).rememberFocus()
                    .then(if (onLongClick != null) Modifier.okClicks(onClick, onLongClick) else Modifier)
                    .then(if (onFocus != null) Modifier.focusReport(focusDelayMs, onFocus) else Modifier),
                shape = ClickableSurfaceDefaults.shape(CardShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = C.panel, focusedContainerColor = C.panel),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                border = FocusRing,
            ) {
                Box(Modifier.fillMaxSize()) {
                    PosterImage(item.image, item.title, Modifier.fillMaxSize())
                    if (watched) Row(Modifier.padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xBF000000)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = Color(0xFF6EE7B7)); Spacer(Modifier.width(3.dp))
                        Text("İzlendi", fontSize = 10.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.SemiBold)
                    }
                    if (rating != null && rating > 0 && rating < 9.3) RatingPill(rating, Modifier.padding(6.dp))
                    if (badge != null) Text(badge, Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(C.primary).padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (progress != null && progress > 0.01f && !watched) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 4.dp, track = Color(0x99000000))
                }
            }
            if (showText) {
                Spacer(Modifier.height(8.dp))
                Text(cardTitle(item.title), style = MaterialTheme.typography.bodyMedium, color = Color(0xE6FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showYear) MetaRow(listOf(item.year, com.fitifiti.tv.domain.formatGenres(item.genre, 1)), color = C.faint)
            }
        }
    }
}

/** Afiş yoksa/kırıksa: koyu zemin üzerinde başlık */
@Composable
fun PosterImage(url: String?, title: String, modifier: Modifier) {
    if (url.isNullOrBlank()) { PosterFallback(title, modifier); return }
    SubcomposeAsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier,
        loading = { Skeleton(Modifier.fillMaxSize(), 0.dp) }, error = { PosterFallback(title, Modifier.fillMaxSize()) })
}

@Composable
private fun PosterFallback(title: String, modifier: Modifier) = Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B1B2A), C.panel))).padding(12.dp), contentAlignment = Alignment.BottomStart) {
    Text(cardTitle(title), style = MaterialTheme.typography.titleMedium, color = Color(0xB3FFFFFF), maxLines = 4)
}

/** Yatay kart (devam et, bölümler, kanallar): 16:9 görsel + ilerleme */
@Composable
fun LandscapeCard(title: String, subtitle: String?, image: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
                  width: androidx.compose.ui.unit.Dp = 300.dp, progress: Float? = null, label: String? = null, onFocus: (() -> Unit)? = null,
                  imageFit: ContentScale = ContentScale.Crop, onLongClick: (() -> Unit)? = null) {
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).rememberFocus()
                .then(if (onLongClick != null) Modifier.okClicks(onClick, onLongClick) else Modifier).then(if (onFocus != null) Modifier.focusReport(onFocus = onFocus) else Modifier),
            shape = ClickableSurfaceDefaults.shape(CardShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.panel, focusedContainerColor = C.panel),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = FocusRing,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!image.isNullOrBlank()) AsyncImage(model = image, contentDescription = null, contentScale = imageFit, modifier = Modifier.fillMaxSize().then(if (imageFit == ContentScale.Fit) Modifier.padding(18.dp) else Modifier))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
                if (label != null) Text(label, Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                else if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color(0xE6FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Başlıklı yatay afiş şeridi */
@Composable
fun PosterRow(title: String, items: List<Item>, onOpen: (Item) -> Unit, modifier: Modifier = Modifier, ranked: Boolean = false,
              progress: (Item) -> Float? = { null }, watched: (Item) -> Boolean = { false }, badge: (Item) -> String? = { null }, onFocusItem: ((Item) -> Unit)? = null) {
    if (items.isEmpty()) return
    Column(modifier.padding(bottom = 28.dp)) {
        SectionTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(items, key = { _, it -> it.key }) { i, it ->
                WideCard(it, onClick = { onOpen(it) }, rank = if (ranked) i + 1 else null, progress = progress(it), watched = watched(it), badge = badge(it),
                    onFocus = onFocusItem?.let { f -> { f(it) } })
            }
        }
    }
}
