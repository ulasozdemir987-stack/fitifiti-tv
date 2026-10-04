package com.fitifiti.tv.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/** Odaktaki içerik vitrinde: üstte sabit bilgi alanı, altında şeritler; şerit odağa gelince listenin tepesine hizalanır. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroRowsLayout(
    initial: Item?,
    heroLabel: String? = null,
    requestInitialFocus: Boolean = false,
    rows: LazyListScope.(onFocusItem: (Item) -> Unit) -> Unit,
) {
    val primary = remember { FocusRequester() }
    if (requestInitialFocus) LaunchedEffect(Unit) { kotlinx.coroutines.delay(150); runCatching { primary.requestFocus() } }
    var shown by remember { mutableStateOf(initial) }
    LaunchedEffect(initial?.key) { if (shown == null) shown = initial }
    val art = rememberArt(shown)
    val listState = rememberLazyListState()
    val topBar = LocalTopBar.current

    // Netflix / Prime tarzı: Aşağı kaydırınca üst çubuğu gizle, en tepedeyken göster
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        topBar.hidden = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(art)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec(36.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item(key = "hero_info") {
                    HeroInfo(
                        shown, art, heroLabel,
                        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
                        primary
                    )
                }
                rows { shown = it }
            }
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
fun HeroInfo(item: Item?, art: HeroArt, label: String?, modifier: Modifier, primary: FocusRequester? = null) {
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
        HeroButtons(item, progress, favorites.any { it.key == item.key }, primary)
    }
}

@Composable
fun HeroButtons(item: Item, progress: Map<String, ProgressEntity>, fav: Boolean, primaryFocus: FocusRequester? = null) {
    val app = App.instance
    val actions = LocalActions.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                        WideCard(it, onClick = { open(actions, it) }, width = if (ranked) 190.dp else width, rank = if (ranked) i + 1 else null,
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
