package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.tv.material3.*
import com.fitifiti.tv.domain.cardTitle
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.Catalog
import com.fitifiti.tv.domain.Ranking
import com.fitifiti.tv.domain.categoryLabel
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*

private enum class MediaSort(val label: String) { New("Yeni eklenen"), Top("En beğenilen"), Year("Yıl"), Az("A–Z") }

/**
 * Filmler / Diziler sekmesi — OwnTV'nin "sinematik" düzeni: odaktaki içeriğin sahne görseli tüm ekranın arkasında,
 * üstte başlık (logo) · meta · özet · oyuncular, altında araç çubuğu (sıralama, kategori) ve afiş ızgarası.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(kind: String) {
    val app = App.instance
    val actions = LocalActions.current
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val favs by app.user.favorites.collectAsStateWithLifecycle()
    val movie = kind == "movie"
    val cats = remember(cat, kind) { categoryItems(cat, kind) }
    var catId by androidx.compose.runtime.saveable.rememberSaveable(kind) { mutableStateOf<String?>(null) }
    var sort by androidx.compose.runtime.saveable.rememberSaveable(kind) { mutableStateOf(MediaSort.New) }
    val catName = cats.firstOrNull { it.first.id == catId }?.first?.name
    val list: List<Item> = remember(cat, catId, sort, kind) {
        val base: List<Item> = if (movie) cat.movies.filter { catId == null || it.categoryId == catId }.map { it.item() }
        else cat.series.filter { catId == null || it.categoryId == catId }.map { it.item() }
        fun added(i: Item) = when (i) { is Item.M -> i.m.added; is Item.S -> i.s.added }
        fun rating(i: Item) = when (i) { is Item.M -> i.m.rating; is Item.S -> i.s.rating }.let { if (it >= 9.3) 0.0 else it }
        when (sort) {
            MediaSort.New -> base.sortedByDescending(::added)
            MediaSort.Top -> base.sortedByDescending(::rating)
            MediaSort.Year -> base.sortedByDescending { it.year?.take(4)?.toIntOrNull() ?: 0 }
            MediaSort.Az -> base.sortedBy { cardTitle(it.title).lowercase(java.util.Locale("tr", "TR")) }
        }.distinctBy { it.key }
    }
    // Odak değişimi yalnız arka plan + başlık katmanlarını yeniden çizer (ızgara ve araç çubuğu etkilenmez)
    val focused = remember(kind) { mutableStateOf<Item?>(null) }
    val shownArt = remember(kind) { mutableStateOf<Pair<Item?, HeroArt>>(null to HeroArt(null, null, null, null)) }
    FocusedArtEffect(list, focused, shownArt)
    var dialog by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<Item?>(null) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    LaunchedEffect(catId, sort) { runCatching { gridState.scrollToItem(0) } }

    Box(Modifier.fillMaxSize().bleedStart()) {
        BackdropLayer(shownArt)
        Column(Modifier.fillMaxSize().padding(start = RailInset + 48.dp, end = 40.dp, top = 22.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (movie) "Filmler" else "Diziler", style = Display.copy(fontSize = 28.sp))
                Spacer(Modifier.width(14.dp))
                Text(categoryLabel(catName).ifBlank { null } ?: if (movie) "Tüm filmler" else "Tüm diziler", color = C.teal, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                Text(" · ${"%,d".format(list.size).replace(',', '.')} içerik", color = C.muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(206.dp)) { val (it, a) = shownArt.value; CinematicInfo(it, a, kind) }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolChip(Icons.AutoMirrored.Filled.Sort, "Sırala", sort.label) { dialog = "sort" }
                ToolChip(Icons.AutoMirrored.Filled.List, "Kategori", catName?.let { categoryLabel(it) } ?: "Tümü") { dialog = "cat" }
                Spacer(Modifier.weight(1f))
                KeyHint("OK", "Aç"); KeyHint("Basılı OK", "Seçenekler")
            }
            if (list.isEmpty()) Text("Bu kategoride içerik yok", color = C.muted, modifier = Modifier.padding(top = 24.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8), state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list.size, key = { list[it].key }) { i ->
                    val it = list[i]
                    val p = progress[it.key]
                    PosterCard(it, onClick = { open(actions, it) }, width = androidx.compose.ui.unit.Dp.Unspecified, modifier = Modifier.fillMaxWidth(),
                        progress = p?.fraction, watched = it is Item.M && p?.finished == true,
                        rating = when (it) { is Item.M -> it.m.rating; is Item.S -> it.s.rating },
                        onFocus = { focused.value = it }, focusDelayMs = 250, onLongClick = { menu = it }, showYear = false)
                }
            }
        }
    }
    when (dialog) {
        "sort" -> OptionsDialog("Sırala", MediaSort.entries.map { s -> (if (s == sort) "✓  " else "") + s.label to { sort = s } }) { dialog = null }
        "cat" -> OptionsDialog("Kategori", listOf((if (catId == null) "✓  " else "") + "Tümü" to { catId = null }) +
            cats.map { (c, items) -> (if (c.id == catId) "✓  " else "") + categoryLabel(c.name) + "  (${items.size})" to { catId = c.id } }) { dialog = null }
    }
    menu?.let { m ->
        val fav = favs.any { it.key == m.key }
        OptionsDialog(cardTitle(m.title), buildList {
            when (m) {
                is Item.M -> { add((if (progress[m.key]?.let { !it.finished && it.positionMs > 15_000 } == true) "Devam et" else "Oynat") to { actions.playMovie(m.m) }); add("Detaylar" to { actions.openMovie(m.m) }) }
                is Item.S -> add("Bölümleri gör" to { actions.openSeries(m.s) })
            }
            add((if (fav) "Listemden çıkar" else "Listeme ekle") to { when (m) { is Item.M -> app.user.toggleFavorite(m.m); is Item.S -> app.user.toggleFavorite(m.s) } })
        }) { menu = null }
    }
}

/** Odaktaki içeriğin görselini çözer; sonucu durum nesnesine yazar (bu bileşenin yeniden çizimi ebeveyni etkilemez) */
@Composable
fun FocusedArtEffect(list: List<Item>, focused: State<Item?>, out: MutableState<Pair<Item?, HeroArt>>) {
    val f = focused.value
    val shown = f?.takeIf { x -> list.any { it.key == x.key } } ?: list.firstOrNull()
    val art = rememberArt(shown)
    SideEffect { out.value = shown to art }
}

@Composable
fun BackdropLayer(state: State<Pair<Item?, HeroArt>>) { FullBleedBackdrop(state.value.second) }

/** Sinematik başlık bloğu: logo/ad, yıl · türler · ★ puan · süre, 2 satır özet, oyuncular (TMDB) */
@Composable
private fun CinematicInfo(item: Item?, art: HeroArt, kind: String) {
    if (item == null) return
    val app = App.instance
    val cast by produceState(emptyList<com.fitifiti.tv.data.tmdb.CastMember>(), art.tmdbId) {
        value = art.tmdbId?.let { id -> runCatching { app.art.cast(if (kind == "movie") "movie" else "series", id) }.getOrNull() }.orEmpty()
    }
    Column {
        HeroTitle(cardTitle(item.title), art.logo, maxWidthFraction = 0.42f, maxLogoHeight = 72.dp)
        Spacer(Modifier.height(8.dp))
        val rating = when (item) { is Item.M -> item.m.rating; is Item.S -> item.s.rating }
        val runtime = (item as? Item.M)?.m?.runtimeMin?.takeIf { it > 0 }?.let { com.fitifiti.tv.domain.formatDuration(it, "minutes") }
        MetaRow(listOf(item.year, com.fitifiti.tv.domain.formatGenres(item.genre, 2), runtime), color = Color(0xD9FFFFFF),
            rating = if (art.vote > 0 && art.votes >= 25) art.vote else rating.takeIf { it > 0 && it < 9.3 })
        Spacer(Modifier.height(6.dp))
        val overview = art.overview ?: when (item) { is Item.M -> item.m.plot; is Item.S -> item.s.plot }
        if (!overview.isNullOrBlank()) Text(overview, style = MaterialTheme.typography.bodyMedium, color = C.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.6f))
        if (cast.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                cast.take(6).forEach { c ->
                    Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(C.fill3), contentAlignment = Alignment.Center) {
                            if (c.photo != null) coil.compose.AsyncImage(model = c.photo, contentDescription = c.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(c.name.take(1), color = C.muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(c.name, fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xCCFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Araç çubuğu düğmesi: "Sırala: Yeni eklenen ▾" */
@Composable
private fun ToolChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.glass(RoundedCornerShape(10.dp), strength = 0.85f), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("$label: ", fontSize = 13.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
        }
    }
}

/** Sağdaki tuş ipucu: [OK] Aç */
@Composable
fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(key, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0x26FFFFFF)).padding(horizontal = 6.dp, vertical = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = C.muted)
        Spacer(Modifier.width(10.dp))
    }
}

/** Sıralı kategoriler + her birinin en yeni içerikleri (boş kategoriler atlanır) */
fun categoryItems(cat: Catalog, kind: String): List<Pair<com.fitifiti.tv.data.xtream.Category, List<Item>>> {
    val movie = kind == "movie"
    val byCat: Map<String?, List<Item>> = if (movie) cat.movies.sortedByDescending { it.added }.groupBy({ it.categoryId }, { it.item() })
    else cat.series.sortedByDescending { it.added }.groupBy({ it.categoryId }, { it.item() })
    return cat.sortedCats(if (movie) cat.vodCats else cat.seriesCats).distinctBy { it.id }.mapNotNull { c -> byCat[c.id]?.takeIf { it.isNotEmpty() }?.let { c to it } }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.categoryRow(kind: String, cats: List<Pair<com.fitifiti.tv.data.xtream.Category, List<Item>>>) {
    if (cats.isEmpty()) return
    item(key = "kesfet") {
        val actions = LocalActions.current
        Column(Modifier.graphicsLayer().padding(bottom = 22.dp)) { // şerit kendi katmanında: dikey kaydırmada baştan çizilmez
            SectionTitle("Keşfet")
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(cats, key = { it.first.id }) { (c, items) ->
                        CategoryCard(c.name, { actions.openCategory(kind, c.id) }, posters = items.mapNotNull { it.image }.take(3))
                    }
                }
            }
        }
    }
}
