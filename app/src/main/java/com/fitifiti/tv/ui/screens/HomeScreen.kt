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
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*

private var initialFocusDone = false

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

    HeroRowsLayout(first, requestInitialFocus = doFocus) { onFocus ->
        continueRow(cont, onFocus)
        posterRow("listem", "Listem", listem, onFocus)
        posterRow("fm", "Öne çıkan filmler", rank.featuredMovies.map { it.item() }, onFocus)
        posterRow("fs", "Öne çıkan diziler", rank.featuredSeries.map { it.item() }, onFocus)
        channelRow("live", "Canlı TV", channels, recentCh.map { it.channelId }.toSet())
        posterRow("nm", "Yeni eklenen filmler", rank.newMovies.map { it.item() }, onFocus)
        posterRow("ns", "Yeni eklenen diziler", rank.newSeries.map { it.item() }, onFocus)
        posterRow("tm", "En beğenilen filmler", rank.topMovies.map { it.item() }, onFocus, ranked = true)
        rank.genreRows("movie", 3).forEach { (g, list) -> posterRow("g-$g", g, list.map { (it as Movie).item() }, onFocus) }
        rank.genreRows("series", 2).forEach { (g, list) -> posterRow("gs-$g", "$g dizileri", list.map { (it as Series).item() }, onFocus) }
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
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(channels, key = { it.id }) { ch ->
                        ChannelCard(ch, { actions.playChannel(ch.id, ids) }, width = 220.dp, label = if (ch.id in recent) "son izlenen" else null)
                    }
                }
            }
        }
    }
}
