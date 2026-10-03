package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.domain.Ranking
import com.fitifiti.tv.domain.categoryStyle
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay

private enum class Sort(val label: String) { New("Yeni eklenen"), Top("En yüksek puan"), Az("A–Z") }

/** Kategori / platform / tür sayfası: başlık (logo), sıralama, tür çipleri, afiş ızgarası */
@Composable
fun CategoryScreen(kind: String, categoryId: String?, genre: String?) {
    val app = App.instance
    val actions = LocalActions.current
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val rawName = categoryId?.let { cat.catName[it] } ?: genre ?: ""
    val st = categoryStyle(rawName)
    var sort by remember { mutableStateOf(Sort.New) }
    var genreFilter by remember { mutableStateOf<String?>(null) }
    val all: List<Item> = remember(cat, kind, categoryId, genre) {
        val m = kind == "movie"
        val src: List<Item> = if (m) cat.movies.map { it.item() } else cat.series.map { it.item() }
        src.filter { i ->
            val c = when (i) { is Item.M -> i.m.categoryId; is Item.S -> i.s.categoryId }
            (categoryId == null || c == categoryId) && (genre == null || Ranking.splitGenres(i.genre).contains(genre))
        }
    }
    val genres = remember(all) {
        all.flatMap { Ranking.splitGenres(it.genre) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(12).filter { it.value >= 3 && it.key != genre }.map { it.key }
    }
    val shown = remember(all, sort, genreFilter) {
        val f = if (genreFilter == null) all else all.filter { Ranking.splitGenres(it.genre).contains(genreFilter) }
        fun rating(i: Item) = when (i) { is Item.M -> i.m.rating; is Item.S -> i.s.rating }.let { if (it >= 9.3) 0.0 else it }
        fun added(i: Item) = when (i) { is Item.M -> i.m.added; is Item.S -> i.s.added }
        when (sort) {
            Sort.New -> f.sortedByDescending { added(it) }
            Sort.Top -> f.sortedByDescending { rating(it) }
            Sort.Az -> f.sortedBy { com.fitifiti.tv.domain.foldTr(com.fitifiti.tv.domain.cardTitle(it.title)) }
        }
    }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(250.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 48.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    if (st.logo != null) AsyncImage(model = brandLogo(st.logo), contentDescription = st.label, contentScale = ContentScale.Fit, colorFilter = brandTint(st.logo),
                        alignment = androidx.compose.ui.Alignment.CenterStart, modifier = Modifier.height(56.dp).widthIn(max = 260.dp))
                    else Text(if (genre != null && categoryId == null) genre else st.label, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("${shown.size} ${if (kind == "movie") "film" else "dizi"}", style = MaterialTheme.typography.bodyMedium, color = C.muted)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Sort.entries.forEachIndexed { i, s -> Chip(s.label, sort == s, { sort = s }, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) }
                    }
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { Chip("Tüm türler", genreFilter == null, { genreFilter = null }) }
                            rowItems(genres) { g -> Chip(g, genreFilter == g, { genreFilter = if (genreFilter == g) null else g }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Burada içerik yok") }
            items(shown.distinctBy { it.key }, key = { it.key }) { i ->
                val p = progress[i.key]
                WideCard(i, onClick = { open(actions, i) }, width = 250.dp, showText = true, progress = p?.fraction, watched = i is Item.M && p?.finished == true)
            }
        }
    }
}
