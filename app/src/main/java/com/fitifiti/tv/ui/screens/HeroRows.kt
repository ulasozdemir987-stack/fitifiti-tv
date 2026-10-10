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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
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
import com.fitifiti.tv.ui.theme.Display
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
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
    header: (@Composable () -> Unit)? = null,
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
    val heroHeight = (com.fitifiti.tv.ui.theme.screenHeightDp() * 0.64f).dp
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
            override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = SnappyScroll
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (hero.focused) {
                    if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset.toFloat() else offset - hero.buttonsOffset
                } else offset - rowTop
        }
    }

    val heroPx = with(density) { heroHeight.toPx() }
    // tam ekran vitrin görseli: sayfanın arkasında sabit durur, şeritlere inildikçe söner (OwnTV düzeni)
    val heroAlpha by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 0f else 1f - (listState.firstVisibleItemScrollOffset / (heroPx * 0.8f)).coerceIn(0f, 1f) } }

    Box(Modifier.fillMaxSize().bleedStart().onPreviewKeyEvent { hero.lastKey = SystemClock.uptimeMillis(); false }) {
        FullBleedBackdrop(art, hide = { 1f - heroAlpha })
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = RailInset), contentPadding = PaddingValues(bottom = 120.dp)) {
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
                        if (header != null) {
                            Box(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 24.dp)) { header() }
                        }
                        HeroInfo(shown, art, shown?.let(heroLabel), Modifier.align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, bottom = 22.dp), primary,
                            dots = if (items.size > 1) ({ HeroDots(items.size, index % items.size, Modifier) }) else null) { hero.buttonsTop = it }
                    }
                }
                rows { }
            }
        }
    }
}

@Composable
private fun heroRequest(url: String): coil.request.ImageRequest {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(url) { coil.request.ImageRequest.Builder(ctx).data(url).size(1280, 720).build() }
}

/**
 * Tam ekran vitrin görseli (OwnTV): sahne görseli tüm ekranı kaplar, sağ üste yaslı; soldan yazı okunsun diye koyulaşır,
 * alttan zemine karışır. Görsel yoksa afişin bulanık rengi + sağda afiş.
 */
@Composable
fun FullBleedBackdrop(art: HeroArt, modifier: Modifier = Modifier, hide: () -> Float = { 0f }) {
    // Performans (zayıf TV ekran kartı): eskiden üst üste 5-6 tam ekran katman + saydamlık katmanı vardı (kare başına
    // ~27 ms GPU). Şimdi görsel + tek bir çizim katmanı; karartmalar yalnız gereken bantlara çizilir, ışımalar yalnız
    // kendi çevrelerine. Sayfa aşağı kaydıkça görsel saydamlık katmanıyla değil üstüne zemin rengi çizilerek söner,
    // tamamen gizlenince görsel hiç çizilmez.
    val glow = rememberAmbient(art.backdrop ?: art.poster)
    val hidden by remember { derivedStateOf { hide() >= 0.999f } }
    Box(modifier.fillMaxSize()) {
        if (!hidden) androidx.compose.animation.Crossfade(targetState = art.backdrop to art.poster, animationSpec = tween(600), label = "bleed") { (bd, poster) ->
            Box(Modifier.fillMaxSize()) {
                // 1280 px yeter (TV'de fark görünmez): 1920×1080 çözüp yüklemek her vitrin dönüşünde takılma yapıyordu
                if (bd != null) coil.compose.AsyncImage(model = heroRequest(bd), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    alignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize())
                else if (poster != null) {
                    // sahne görseli yoksa: sağda afişin kendisi (bulanık tam ekran kopya ekran kartını çok yoruyordu)
                    coil.compose.AsyncImage(model = poster, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 110.dp).fillMaxHeight(0.55f).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
                }
            }
        }
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val w = size.width; val h = size.height
            val left = Brush.horizontalGradient(0f to C.bg.copy(alpha = 0.94f), 0.43f to C.bg.copy(alpha = 0.72f), 0.74f to C.bg.copy(alpha = 0.2f), 1f to Color.Transparent, endX = w * 0.7f)
            val top = Brush.verticalGradient(0f to C.bg.copy(alpha = 0.45f), 1f to Color.Transparent, endY = h * 0.14f)
            val bottom = Brush.verticalGradient(0f to Color.Transparent, 0.64f to C.bg.copy(alpha = 0.85f), 1f to C.bg, startY = h * 0.5f, endY = h)
            val r1 = w * 0.42f; val r2 = w * 0.5f
            val g1 = Brush.radialGradient(listOf(glow.copy(alpha = 0.30f), Color.Transparent), center = Offset.Zero, radius = r1)
            val g2 = Brush.radialGradient(listOf(glow.copy(alpha = 0.10f), Color.Transparent), center = Offset(w, h), radius = r2)
            onDrawBehind {
                val k = hide()
                if (k >= 0.999f) return@onDrawBehind // zemin zaten uygulamanın arka planı
                drawRect(left, size = Size(w * 0.7f, h))
                drawRect(top, size = Size(w, h * 0.14f))
                drawRect(bottom, topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                drawRect(g1, size = Size(r1, minOf(r1, h)))
                drawRect(g2, topLeft = Offset(w - r2, (h - r2).coerceAtLeast(0f)), size = Size(r2, minOf(r2, h)))
                if (k > 0.001f) drawRect(C.bg.copy(alpha = k))
            }
        })
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


/** Vitrin sırası (sitedeki noktalar): etkin olan uzun beyaz çizgi */
@Composable
private fun HeroDots(count: Int, active: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == active) 30.dp else 16.dp, tween(500), label = "dot")
            Box(Modifier.width(w).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == active) C.progress else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.25f)))))
        }
    }
}

/** Odak kaydırması: varsayılan yay (~0,5 sn) kumandada gecikmeli hissettiriyordu → kısa ve keskin */
val SnappyScroll: androidx.compose.animation.core.AnimationSpec<Float> = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)

/** Yatay şerit: odaktaki kart kenar boşluğunun içinde kalacak kadar kaydır */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberRowSpec(pad: Dp = 48.dp): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density, pad) {
        val p = with(density) { pad.toPx() }
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = SnappyScroll
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
fun HeroInfo(item: Item?, art: HeroArt, label: String?, modifier: Modifier, primary: FocusRequester? = null, dots: (@Composable () -> Unit)? = null, onButtonsPositioned: (Float) -> Unit = {}) {
    val app = App.instance
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val favorites by app.user.favorites.collectAsStateWithLifecycle()
    Column(modifier) {
        if (item == null) return@Column
        // Vitrin dönerken hiçbir şey kaymaz: etiket, başlık, meta ve özet SABİT yükseklikte yuvalarda; yazı katmanı
        // yumuşakça değişir (eskiden logo/ad yüksekliği ve özet satır sayısı değiştikçe düğmeler zıplıyordu)
        androidx.compose.animation.AnimatedContent(
            targetState = Triple(item, art, label), contentKey = { it.first.key },
            transitionSpec = { (fadeIn(tween(450, delayMillis = 120)) togetherWith fadeOut(tween(200))) },
            label = "heroText",
        ) { (it, a, lb) -> HeroText(it, a, lb) }
        Spacer(Modifier.height(18.dp))
        HeroButtons(item, progress, favorites.any { it.key == item.key }, primary, onButtonsPositioned)
        if (dots != null) { Spacer(Modifier.height(18.dp)); dots() }
    }
}

@Composable
private fun HeroText(item: Item, art: HeroArt, label: String?) {
    Column(Modifier.fillMaxWidth(0.5f)) {
        Box(Modifier.height(20.dp)) { if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = C.teal, letterSpacing = 0.02.em) }
        Spacer(Modifier.height(6.dp))
        // başlık yuvası: logo da yazı da alta hizalı, sabit yükseklik
        Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
            androidx.compose.animation.Crossfade(art.logo, animationSpec = tween(350), label = "logo") { logo ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                    if (logo != null) coil.compose.AsyncImage(model = logo, contentDescription = item.title, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        alignment = Alignment.BottomStart, modifier = Modifier.fillMaxWidth(0.82f).heightIn(max = 92.dp))
                    else Text(cardTitle(item.title), style = Display.copy(fontSize = 44.sp, lineHeight = 46.sp, shadow = TitleShadow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val runtime = (item as? Item.M)?.m?.runtimeMin?.takeIf { it > 0 }?.let { com.fitifiti.tv.domain.formatDuration(it, "minutes") }
        MetaRow(listOf(item.year, runtime, com.fitifiti.tv.domain.formatGenres(item.genre, 2)), color = Color(0xE6FFFFFF),
            rating = if (art.vote > 0 && art.votes >= 25) art.vote else null, modifier = Modifier.height(24.dp))
        Spacer(Modifier.height(10.dp))
        val overview = art.overview ?: when (item) { is Item.M -> item.m.plot; is Item.S -> item.s.plot }
        // özet her zaman 3 satırlık yer kaplar (kısa özet düğmeleri yukarı çekmesin)
        Text(overview?.takeIf { it.isNotBlank() } ?: "", style = MaterialTheme.typography.bodyLarge.copy(shadow = TextShadow), color = Color(0xC7FFFFFF),
            minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

private val TitleShadow = androidx.compose.ui.graphics.Shadow(Color(0x99000000), androidx.compose.ui.geometry.Offset(0f, 2f), 12f)
private val TextShadow = androidx.compose.ui.graphics.Shadow(Color(0x80000000), androidx.compose.ui.geometry.Offset(0f, 1f), 6f)

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
                Btn("Detaylar", { actions.openMovie(item.m) }, kind = BtnKind.Secondary, icon = Icons.Default.Info)
                IconAction(if (fav) Icons.Default.Check else Icons.Default.Add, if (fav) "Listemden çıkar" else "Listeme ekle", { app.user.toggleFavorite(item.m) }, active = fav, showLabel = false)
            }
            is Item.S -> {
                val last = progress.values.filter { it.seriesId == item.s.id }.maxByOrNull { it.updatedAt }
                if (last != null && !last.finished) Btn("Devam et · ${last.episodeNum ?: ""}. bölüm", { actions.playContinue(last) }, Modifier.then(primaryFocus?.let { Modifier.focusRequester(it) } ?: Modifier), icon = Icons.Default.PlayArrow)
                if (last == null || last.finished) Btn("Bölümler", { actions.openSeries(item.s) }, Modifier.then(primaryFocus?.let { Modifier.focusRequester(it) } ?: Modifier), icon = Icons.Default.PlayArrow)
                else Btn("Bölümler", { actions.openSeries(item.s) }, kind = BtnKind.Secondary, icon = Icons.Default.Info)
                IconAction(if (fav) Icons.Default.Check else Icons.Default.Add, if (fav) "Listemden çıkar" else "Listeme ekle", { app.user.toggleFavorite(item.s) }, active = fav, showLabel = false)
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
        Column(Modifier.graphicsLayer().padding(bottom = 14.dp)) { // şerit kendi katmanında: dikey kaydırmada baştan çizilmez
            SectionTitle(title)
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(items, key = { _, it -> it.key }) { i, it ->
                        val p = progress[it.key]
                        if (ranked) {
                            PosterCard(it, onClick = { open(actions, it) }, width = 160.dp, rank = i + 1,
                                progress = p?.fraction, watched = it is Item.M && p?.finished == true, badge = badge(it), onFocus = { onFocusItem(it) })
                        } else {
                            WideCard(it, onClick = { open(actions, it) }, width = width,
                                progress = p?.fraction, watched = it is Item.M && p?.finished == true, badge = badge(it), onFocus = { onFocusItem(it) })
                        }
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
        Column(Modifier.graphicsLayer().padding(bottom = 14.dp)) { // şerit kendi katmanında: dikey kaydırmada baştan çizilmez
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
