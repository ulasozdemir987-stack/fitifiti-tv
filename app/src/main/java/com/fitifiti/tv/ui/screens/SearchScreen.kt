package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.domain.Ranking
import com.fitifiti.tv.domain.SearchIndex
import com.fitifiti.tv.domain.foldTr
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Arama: Türkçe/aksan duyarsız, yazım hatasına toleranslı (cihazdaki dizin); kanallar adla */
@Composable
fun SearchScreen() {
    val app = App.instance
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val recent by app.user.recentSearches.collectAsStateWithLifecycle()
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    var result by remember { mutableStateOf<SearchIndex.Result?>(null) }
    LaunchedEffect(q, cat) {
        if (q.trim().length < 2) { result = null; return@LaunchedEffect }
        delay(250)
        result = withContext(Dispatchers.Default) { app.catalog.search(q) }
    }
    val channels = remember(q, cat) {
        val f = foldTr(q)
        if (f.length < 2) emptyList() else cat.channels.filter { foldTr(it.name).contains(f) }.take(20)
    }
    LaunchedEffect(q) { if (q.trim().length >= 3) { delay(2500); app.user.addSearch(q) } }
    val rank = remember(cat) { Ranking.of(cat) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 100.dp, bottom = 80.dp)) {
        item(key = "field") {
            Column(Modifier.padding(horizontal = 48.dp).padding(bottom = 22.dp)) {
                TvTextField(q, { q = it }, "", Modifier.fillMaxWidth(0.6f), placeholder = "Film, dizi, kanal ara", icon = Icons.Default.Search, imeAction = ImeAction.Search, onDone = { app.user.addSearch(q) })
                if (q.isBlank() && recent.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Son aramalar", style = MaterialTheme.typography.labelLarge, color = C.muted)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recent.distinctBy { it.query }, key = { it.query }) { r -> Chip(r.query, false, { q = r.query }) }
                        item { Chip("Temizle", false, { app.user.clearSearches() }) }
                    }
                }
                result?.let { r -> if (r.fuzzy && (r.movies.isNotEmpty() || r.series.isNotEmpty())) { Spacer(Modifier.height(10.dp)); Text("Tam eşleşme yok, yakın sonuçlar gösteriliyor", style = MaterialTheme.typography.bodySmall, color = C.faint) } }
            }
        }
        val r = result
        if (q.trim().length < 2) {
            plainPosterRow("s-fm", "Öne çıkan filmler", rank.featuredMovies.take(20).map { it.item() })
            plainPosterRow("s-fs", "Öne çıkan diziler", rank.featuredSeries.take(20).map { it.item() })
        } else if (r != null) {
            if (r.movies.isEmpty() && r.series.isEmpty() && channels.isEmpty()) item(key = "none") { EmptyState("“$q” için sonuç yok", "Başka bir yazım dene ya da daha kısa yaz.") }
            plainPosterRow("r-m", "Filmler", r.movies.map { it.item() })
            plainPosterRow("r-s", "Diziler", r.series.map { it.item() })
            channelRow("r-c", "Kanallar", channels)
        }
    }
}
