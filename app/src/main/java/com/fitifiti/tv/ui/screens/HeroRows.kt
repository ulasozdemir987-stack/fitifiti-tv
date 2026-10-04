package com.fitifiti.tv.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ProgressEntity
import com.fitifiti.tv.domain.cardTitle
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.launch

/**
 * Vitrinli sayfa (sitedeki Dashboard / MediaHome): en üstte sayfayla birlikte kayan vitrin (öne çıkanlar 8 sn'de bir
 * döner), altında şeritler sayfa zemininde. Vitrin görseli YALNIZ vitrin kutusunda: eskiden ekranı kaplayan arka plan
 * odaktaki kartın görseline dönüyordu, aşağı inince şeritlerin arkasında kocaman soluk bir resim kalıyordu (2.9.4).
 * Şeride inince şerit başlığı ekranın tepesine hizalanır (vitrin yukarı kayıp çıkar, üst çubuk gizlenir, aynı anda
 * 3 şerit görünür); vitrin düğmelerine dönünce sayfa en üste kayar.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroRowsLayout(
    initial: Item?,
    heroItems: List<Item> = listOfNotNull(initial),
    heroLabel: (Item) -> String? = { null },
    requestInitialFocus: Boolean = false,
    rows: LazyListScope.(onFocusItem: (Item) -> Unit) -> Unit,
) {
    val items = remember(heroItems) { heroItems.distinctBy { it.key } }
    val primary = remember { FocusRequester() }
    if (requestInitialFocus) LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { primary.requestFocus() } }
    var index by remember { mutableIntStateOf(0) }
    val shown = if (items.isEmpty()) null else items[index % items.size]
    val art = rememberArt(shown)
    rememberArt(if (items.size > 1) items[(index + 1) % items.size] else null) // sıradaki vitrinin görselleri önceden
    val listState = rememberLazyListState()
    val topBar = LocalTopBar.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    val hero = remember { HeroState() }

    // en tepedeyken üst çubuk görünür, şeritlere inince çekilir
    LaunchedEffect(listState) {
        val limit = with(density) { 60.dp.toPx() }
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > limit }.collect { topBar.hidden = it }
    }
    // dönen vitrin (sitedeki gibi 8 sn): vitrin görünürken ve kumandaya 8 sn dokunulmadıysa sıradakine geçer
    LaunchedEffect(items.size) {
        if (items.size < 2) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1000)
            val now = SystemClock.uptimeMillis()
            if (listState.firstVisibleItemIndex != 0 || now - hero.lastKey < 8000 || now - hero.lastSwitch < 8000) continue
            val hadFocus = hero.focused
            index = (index + 1) % items.size
            hero.lastSwitch = now
            // film ↔ dizi geçişinde düğmeler yeniden kurulur; odak kaybolmasın diye asıl düğmeye geri verilir
            if (hadFocus) { withFrameNanos {}; withFrameNanos {}; runCatching { primary.requestFocus() } }
        }
    }
    // dikey kaydırma: vitrin düğmesi odaktaysa sayfa en üste, şeritteyse şerit başlığı ekranın tepesine
    val spec = remember(density) {
        val rowTop = with(density) { (22 + 34).dp.toPx() } // üst boşluk + SectionTitle
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (hero.focused) {
                    if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset.toFloat() else offset - hero.buttonsOffset
                } else offset - rowTop
        }
    }

    Box(Modifier.fillMaxSize().cinematicBackground().onPreviewKeyEvent { hero.lastKey = SystemClock.uptimeMillis(); false }) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth().height(heroHeight)
                        .onGloballyPositioned { hero.heroTop = it.positionInRoot().y }
                        .onFocusChanged { f ->
                            val was = hero.focused
                            hero.focused = f.hasFocus
                            if (f.hasFocus && !was) scope.launch {
                                withFrameNanos {}
                                if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) listState.animateScrollToItem(0)
                            }
                        }
                        .focusGroup()) {
                        HeroBillboardBackdrop(art, Modifier.fillMaxSize())
                        HeroInfo(shown, art, shown?.let(heroLabel), Modifier.align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, bottom = 40.dp), primary) { hero.buttonsTop = it }
                        if (items.size > 1) HeroDots(items.size, index % items.size, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 56.dp))
                    }
                }
                rows { }
            }
        }
    }
}

private class HeroState {
    var focused = false
    var lastKey = 0L
    var lastSwitch = SystemClock.uptimeMillis()
    var heroTop = 0f
    var buttonsTop = 0f
    /** düğme satırının vitrin kutusunun tepesine uzaklığı (px; kaydırmadan bağımsız) */
    val buttonsOffset get() = buttonsTop - heroTop
}

/** Sitedeki `.cinematic-bg`: zemin + sol üstte mor, sağ üstte turkuaz çok hafif ışıma */
private fun Modifier.cinematicBackground() = drawBehind {
    drawRect(C.bg)
    drawRect(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.2f, -size.height * 0.1f), radius = size.width * 0.5f))
    drawRect(Brush.radialGradient(listOf(C.teal.copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.42f))
}

/** Vitrin sırası (sitedeki noktalar): etkin olan uzun beyaz çizgi */
@Composable
private fun HeroDots(count: Int, active: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == active) 22.dp else 6.dp, tween(500), label = "dot")
            Box(Modifier.width(w).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == active) Color.White else Color.White.copy(alpha = 0.3f)))
        }
    }
}

/** Yatay şerit: odaktaki kart kenar boşluğunun içinde kalacak kadar kaydır */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberRowSpec(pad: Dp = 48.dp): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density, pad) {
        val p = with(density) { pad.toPx() }
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                offset < p -> offset - p
                offset + size > containerSize - p -> offset + size - (containerSize - p)
                else -> 0f
            }
        }
    }
}

/** Vitrin bilgisi + eylem düğmeleri (odaktaki içerik için) */
@Composable
fun HeroInfo(item: Item?, art: HeroArt, label: String?, modifier: Modifier, primary: FocusRequester? = null, onButtonsPositioned: (Float) -> Unit = {}) {
    val app = App.instance
    val actions = LocalActions.current
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val favorites by app.user.favorites.collectAsStateWithLifecycle()
    Column(modifier.padding(start = 48.dp, top = 64.dp, end = 48.dp), verticalArrangement = Arrangement.Top) {
        if (item == null) return@Column
        if (label != null) { Text(label, style = MaterialTheme.typography.labelLarge, color = C.muted); Spacer(Modifier.height(8.dp)) }
        HeroTitle(cardTitle(item.title), art.logo, maxWidthFraction = 0.38f, maxLogoHeight = 96.dp)
        Spacer(Modifier.height(8.dp))
        val runtime = (item as? Item.M)?.m?.runtimeMin?.takeIf { it > 0 }?.let { com.fitifiti.tv.domain.formatDuration(it, "minutes") }
        MetaRow(listOf(item.year, runtime, item.genre?.split(',', '/', '&')?.take(2)?.joinToString(", ") { it.trim() },
            if (art.vote > 0 && art.votes >= 25) "TMDB ${"%.1f".format(art.vote)}" else null))
        Spacer(Modifier.height(6.dp))
        val overview = art.overview ?: when (item) { is Item.M -> item.m.plot; is Item.S -> item.s.plot }
        if (!overview.isNullOrBlank()) Text(overview, style = MaterialTheme.typography.bodyMedium, color = C.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.45f))
        Spacer(Modifier.height(12.dp))
        HeroButtons(item, progress, favorites.any { it.key == item.key }, primary, onButtonsPositioned)
    }
}

@Composable
fun HeroButtons(item: Item, progress: Map<String, ProgressEntity>, fav: Boolean, primaryFocus: FocusRequester? = null, onPositioned: (Float) -> Unit = {}) {
    val app = App.instance
    val actions = LocalActions.current
    Row(Modifier.onGloballyPositioned { onPositioned(it.positionInRoot().y) }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        when (item) {
            is Item.M -> {
                val p = progress[item.key]
                val resume = p != null && !p.finished && p.positionMs > 15_000
                Btn(if (resume) "Devam et" else "Oynat", { actions.playMovie(item.m) }, Modifier.then(primaryFocus?.let { Modifier.focusRequester(it) } ?: Modifier), icon = Icons.Default.PlayArrow)
                IconAction(Icons.Default.Info, "Detaylar", { actions.openMovie(item.m) })
                IconAction(if (fav) Icons.Default.Check else Icons.Default.Add, if (fav) "Listemden çıkar" else "Listeme ekle", { app.user.toggleFavorite(item.m) }, active = fav)
            }
            is Item.S -> {
                val last = progress.values.filter { it.seriesId == item.s.id }.maxByOrNull { it.updatedAt }
                if (last != null && !last.finished) Btn("Devam et · ${last.episodeNum ?: ""}. bölüm", { actions.playContinue(last) }, Modifier.then(primaryFocus?.let { Modifier.focusRequester(it) } ?: Modifier), icon = Icons.Default.PlayArrow)
                if (last == null || last.finished) Btn("Bölümler", { actions.openSeries(item.s) }, Modifier.then(primaryFocus?.let { Modifier.focusRequester(it) } ?: Modifier), icon = Icons.Default.PlayArrow)
                else IconAction(Icons.Default.Info, "Bölümler", { actions.openSeries(item.s) })
                IconAction(if (fav) Icons.Default.Check else Icons.Default.Add, if (fav) "Listemden çıkar" else "Listeme ekle", { app.user.toggleFavorite(item.s) }, active = fav)
            }
        }
    }
}

/** Vitrinli sayfalardaki afiş şeridi (ad vitrinde göründüğü için kartın altında yazı yok) */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.posterRow(
    key: String, title: String, all: List<Item>, onFocusItem: (Item) -> Unit, ranked: Boolean = false, width: Dp = 220.dp,
    badge: (Item) -> String? = { null },
) {
    // Aynı anahtar iki kez geçerse LazyRow uygulamayı düşürür (bazı sağlayıcılar aynı içeriği tekrar verir)
    val items = all.distinctBy { it.key }
    if (items.isEmpty()) return
    item(key = key) {
        val app = App.instance
        val actions = LocalActions.current
        val progress by app.user.progressMap.collectAsStateWithLifecycle()
        Column(Modifier.padding(bottom = 14.dp)) {
            SectionTitle(title)
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(items, key = { _, it -> it.key }) { i, it ->
                        val p = progress[it.key]
                        WideCard(it, onClick = { open(actions, it) }, width = if (ranked) 280.dp else width, rank = if (ranked) i + 1 else null,
                            progress = p?.fraction, watched = it is Item.M && p?.finished == true, badge = badge(it), onFocus = { onFocusItem(it) })
                    }
                }
            }
        }
    }
}

fun open(actions: com.fitifiti.tv.ui.Actions, it: Item) = when (it) { is Item.M -> actions.openMovie(it.m); is Item.S -> actions.openSeries(it.s) }

/** "İzlemeye devam et" şeridi: yatay kartlar, sıradaki bölümde etiket */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.continueRow(list: List<ProgressEntity>, onFocusItem: (Item) -> Unit) {
    if (list.isEmpty()) return
    item(key = "continue") {
        val app = App.instance
        val actions = LocalActions.current
        val cat by app.catalog.catalog.collectAsStateWithLifecycle()
        var menu by remember { mutableStateOf<ProgressEntity?>(null) }
        menu?.let { p ->
            val item: Item? = if (p.kind == "movie") cat.movieById[p.itemId.toIntOrNull() ?: -1]?.item() else p.seriesId?.let { cat.seriesById[it] }?.item()
            com.fitifiti.tv.ui.components.OptionsDialog(cardTitle(p.title.substringBefore(" · ")), buildList {
                add((if (p.isUpNext) "Oynat" else "Devam et") to { actions.playContinue(p) })
                if (item != null) add((if (item is Item.S) "Bölümleri gör" else "Detaylar") to { open(actions, item) })
                if (p.durationMs > 0) add("İzlendi olarak işaretle" to { app.user.markFinished(p) })
                add("Devam et'ten kaldır" to { app.user.removeFromContinue(p) })
            }) { menu = null }
        }
        Column(Modifier.padding(bottom = 14.dp)) {
            SectionTitle("İzlemeye devam et")
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list.distinctBy { it.key }, key = { it.key }) { p ->
                    val item: Item? = if (p.kind == "movie") cat.movieById[p.itemId.toIntOrNull() ?: -1]?.item() else p.seriesId?.let { cat.seriesById[it] }?.item()
                    val art = rememberArt(item)
                    val remaining = if (p.durationMs > 0) com.fitifiti.tv.domain.formatDuration((p.durationMs - p.positionMs) / 1000) + " kaldı" else null
                    LandscapeCard(
                        title = cardTitle(p.title.substringBefore(" · ")),
                        subtitle = listOfNotNull(if (p.kind == "episode") "${p.season ?: 1}. Sezon · Bölüm ${p.episodeNum ?: ""}" else null, remaining).joinToString(" · "),
                        image = art.backdrop ?: p.image, onClick = { actions.playContinue(p) }, width = 220.dp,
                        progress = if (p.isUpNext) null else p.fraction, label = if (p.isUpNext) "Sıradaki bölüm" else null,
                        onFocus = item?.let { i -> { onFocusItem(i) } },
                        onLongClick = { menu = p },
                    )
                }
            }
            }
        }
    }
}
