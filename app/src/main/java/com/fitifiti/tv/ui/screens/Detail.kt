package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.lazy.rememberLazyListState
import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.tmdb.CastMember
import com.fitifiti.tv.data.xtream.Variant
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display

/** Detay sayfası iskeleti: kenardan kenara sahne görseli + kaydırılabilir içerik */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScaffold(art: HeroArt, trailer: TrailerSpec? = null, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Fragman yalnız başlık tam görünürken oynar; oyunculara/bölümlere kaydırınca hemen durur ve kararır
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 60 } }
    val trailerState = remember { TrailerState() }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        CompositionLocalProvider(LocalTrailerState provides trailerState) {
        // Arka plan sahnesi: aşağı kaydırırken alttan kesilip havada kalmaz, yumuşakça siyah zemine kararır (alpha fade)
        // Kaydırınca sahne zemin rengine karışır: saydamlık katmanı (tüm sahneyi her karede ekran dışı tampona çizer)
        // YERİNE üstüne zemin rengi çizilir; tamamen kaybolunca sahne hiç çizilmez
        val hideBackdrop by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
        if (!hideBackdrop) HeroBackdrop(art, modifier = Modifier.fillMaxSize(), video = trailer?.let { t -> { TrailerVideo(t, atTop) } })
        Spacer(Modifier.fillMaxSize().drawBehind {
            val k = if (list.firstVisibleItemIndex == 0) (list.firstVisibleItemScrollOffset.toFloat() / (density.density * 220f)).coerceIn(0f, 1f) else 1f
            if (k > 0.001f) drawRect(C.bg.copy(alpha = k))
        })
        // TV'de varsayılan kaydırma odaktaki öğeyi ekranın üst %30'una çeker: "Oynat"a odaklanınca sayfa ~300 px
        // aşağı kayıyor, başlığın üstü kesiliyordu. Yalnız gerektiği kadar kaydır (öğe zaten görünüyorsa hiç kaydırma).
        CompositionLocalProvider(LocalDetailList provides list, LocalBringIntoViewSpec provides rememberRowSpec(24.dp)) {
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 80.dp), content = content)
        }
        }
    }
}

private val LocalDetailList = staticCompositionLocalOf<androidx.compose.foundation.lazy.LazyListState?> { null }

/**
 * Başlık bloğuna (ilk öğe) eklenir: içindeki bir düğme odak alınca liste EN ÜSTE kayar. Odak yalnız düğmeyi ekrana
 * getirdiği için aşağıdan (bölümler/oyuncular) geri gelince başlığın üstü (logo, puanlar) kesik kalıyor ve yukarı
 * odaklanacak bir şey olmadığından kaydırılamıyordu.
 */
@Composable
fun Modifier.detailHead(): Modifier {
    val list = LocalDetailList.current ?: return this
    val scope = rememberCoroutineScope()
    var hadFocus by remember { mutableStateOf(false) }
    // Yalnızca odak dışarıdan (oyuncular/bölümler) başlık alanına İLK girdiğinde en üste kaydır.
    // Düğmeler arasında gezinirken her seferinde kaydırma animasyonu tetiklenmez; kumanda takılmaz.
    return this.onFocusEvent { state ->
        val gained = !hadFocus && state.hasFocus
        hadFocus = state.hasFocus
        if (gained && (list.firstVisibleItemIndex != 0 || list.firstVisibleItemScrollOffset != 0)) {
            scope.launch { androidx.compose.runtime.withFrameNanos { }; list.animateScrollToItem(0) }
        }
    }
}

/** "Sürüm" çipleri (Orijinal dil / Türkçe dublaj / 4K) */
@Composable
fun VariantPicker(variants: List<Variant>, selected: Variant?, onSelect: (Variant) -> Unit) {
    if (variants.size < 2) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sürüm", style = MaterialTheme.typography.labelLarge, color = C.muted)
        Spacer(Modifier.width(4.dp))
        variants.forEach { v -> Chip(v.label, v.id == (selected?.id ?: variants.first().id), { onSelect(v) }) }
    }
}

/** Bilgi satırları: "Yönetmen  …" (çerçevesiz, sitedeki InfoList) */
@Composable
fun InfoList(rows: List<Pair<String, String?>>, modifier: Modifier = Modifier) {
    val list = rows.filter { !it.second.isNullOrBlank() }
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { (k, v) ->
            Row {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = C.faint, modifier = Modifier.width(110.dp))
                Text(v!!, style = MaterialTheme.typography.bodyMedium, color = Color(0xCCFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.castRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    item(key = "cast") {
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionTitle("Oyuncular")
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(cast.distinctBy { it.name + it.role }, key = { it.name + it.role }) { c -> CastCard(c) }
                }
            }
        }
    }
}

@Composable
private fun CastCard(c: CastMember) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = {}, modifier = Modifier.size(96.dp).rememberFocus(),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.fill2, focusedContainerColor = C.fill3),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, com.fitifiti.tv.ui.components.RingBrush), shape = CircleShape)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val initials = c.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }
                Text(initials, style = Display.copy(fontSize = 26.sp, color = Color(0x99FFFFFF)))
                if (c.photo != null) AsyncImage(model = c.photo, contentDescription = c.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(c.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!c.role.isNullOrBlank()) Text(c.role, style = MaterialTheme.typography.labelSmall, color = C.faint, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Afişli şerit (detay sayfalarında, kartın altında ad yazılı) */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.plainPosterRow(key: String, title: String, all: List<Item>) {
    val items = all.distinctBy { it.key }
    if (items.isEmpty()) return
    item(key = key) {
        val actions = LocalActions.current
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionTitle(title)
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(items, key = { it.key }) { WideCard(it, onClick = { open(actions, it) }, width = 196.dp, showText = true) }
                }
            }
        }
    }
}

/** Benzer içerik: aynı kategori + ortak tür, puanı yüksek ve yeni olan önce */
fun <T> similarOf(all: List<T>, self: T, cat: (T) -> String?, genre: (T) -> String?, rating: (T) -> Double, id: (T) -> Int): List<T> {
    val myGenres = com.fitifiti.tv.domain.Ranking.splitGenres(genre(self)).toSet()
    val myCat = cat(self)
    return all.asSequence().filter { id(it) != id(self) }.map { it to (
        (if (cat(it) == myCat) 2.0 else 0.0) + com.fitifiti.tv.domain.Ranking.splitGenres(genre(it)).count { g -> g in myGenres } * 1.5 +
            rating(it).let { r -> if (r in 0.1..9.2) r / 4 else 0.0 })
    }.filter { it.second >= 2.5 }.sortedByDescending { it.second }.take(20).map { it.first }.toList()
}
