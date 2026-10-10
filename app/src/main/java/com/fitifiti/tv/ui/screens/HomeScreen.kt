package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitifiti.tv.App
import com.fitifiti.tv.data.xtream.Channel
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series
import com.fitifiti.tv.domain.Ranking
import com.fitifiti.tv.domain.categoryStyle
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*

private var initialFocusDone = false

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val app = App.instance
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val cont by app.user.continueList.collectAsStateWithLifecycle()
    val favs by app.user.favorites.collectAsStateWithLifecycle()
    val recentCh by app.user.recentChannels.collectAsStateWithLifecycle()
    val rank = remember(cat) { Ranking.of(cat) }

    val listem = remember(favs, cat) { favs.mapNotNull { f -> if (f.kind == "movie") cat.movieById[f.itemId]?.item() else cat.seriesById[f.itemId]?.item() } }
    val first = remember(cat, cont.firstOrNull()?.key) {
        cont.firstOrNull()?.let { p -> if (p.kind == "movie") cat.movieById[p.itemId.toIntOrNull() ?: -1]?.item() else p.seriesId?.let { cat.seriesById[it] }?.item() }
            ?: rank.featuredMovies.firstOrNull()?.item() ?: rank.featuredSeries.firstOrNull()?.item()
    }
    val channels = remember(cat, recentCh) {
        val r = recentCh.mapNotNull { cat.channelById[it.channelId] }
        (r + cat.channels.take(40)).distinctBy { it.id }.take(20)
    }
    val doFocus = remember { !initialFocusDone.also { initialFocusDone = true } }
    // Şerit listeleri bir kez kurulur (vitrin her odak değişiminde yeniden çizilir)
    val rows = remember(rank) {
        object {
            val fm = rank.featuredMovies.map { it.item() }; val fs = rank.featuredSeries.map { it.item() }
            val nm = rank.newMovies.map { it.item() }; val ns = rank.newSeries.map { it.item() }; val tm = rank.topMovies.map { it.item() }
            val gm = rank.genreRows("movie", 3).map { (g, l) -> g to l.map { (it as Movie).item() } }
            val gs = rank.genreRows("series", 2).map { (g, l) -> g to l.map { (it as Series).item() } }
        }
    }

    val platforms = remember {
        listOf("netflix", "amazon", "hbo", "disney", "exxen", "max blu", "gain", "tabii", "b* connect", "apple")
    }

    val heroItems = remember(first, rows) {
        val list = mutableListOf<Item>()
        if (first != null) list.add(first)
        list.addAll(rows.fm.take(5))
        list.addAll(rows.fs.take(5))
        list.distinctBy { it.key }.take(8)
    }

    val firstIsContinue = cont.isNotEmpty() && first != null
    HeroRowsLayout(first, heroItems = heroItems, requestInitialFocus = doFocus,
        heroLabel = { if (firstIsContinue && it.key == first?.key) "Kaldığın yerden" else if (it is Item.M) "Öne çıkan · Filmler" else "Öne çıkan · Diziler" }) { onFocus ->
        continueRow(cont, onFocus)
        item(key = "platforms") {
            val actions = LocalActions.current
            Column(Modifier.padding(bottom = 16.dp)) {
                SectionTitle("Platformlar")
                CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(platforms) { key ->
                            CategoryCard(rawName = key, onClick = { val brandId = categoryStyle(key).logo ?: key; actions.openPlatform(brandId) }, width = 170.dp)
                        }
                    }
                }
            }
        }
        posterRow("listem", "Listem", listem, onFocus)
        posterRow("fm", "Öne çıkan filmler", rows.fm, onFocus)
        posterRow("fs", "Öne çıkan diziler", rows.fs, onFocus)
        channelRow("live", "Canlı TV", channels, recentCh.map { it.channelId }.toSet())
        posterRow("nm", "Yeni eklenen filmler", rows.nm, onFocus)
        posterRow("ns", "Yeni eklenen diziler", rows.ns, onFocus)
        posterRow("tm", "En beğenilen filmler", rows.tm, onFocus, ranked = true)
        rows.gm.forEach { (g, list) -> posterRow("g-$g", g, list, onFocus) }
        rows.gs.forEach { (g, list) -> posterRow("gs-$g", "$g dizileri", list, onFocus) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.channelRow(key: String, title: String, channels: List<Channel>, recent: Set<Int> = emptySet()) {
    if (channels.isEmpty()) return
    item(key = key) {
        val actions = LocalActions.current
        val ids = channels.map { it.id }
        Column(Modifier.padding(bottom = 22.dp)) {
            SectionTitle(title)
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(channels.distinctBy { it.id }, key = { it.id }) { ch ->
                        ChannelCard(ch, { actions.playChannel(ch.id, ids) }, width = 220.dp, label = if (ch.id in recent) "son izlenen" else null)
                    }
                }
            }
        }
    }
}
