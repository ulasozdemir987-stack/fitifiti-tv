package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C

private enum class Kind(val label: String) { All("Tümü"), Movies("Filmler"), Series("Diziler") }
private enum class Status(val label: String) { All("Hepsi"), NotStarted("Başlamadıklarım"), Partial("Yarım kalanlar"), Done("Bitenler") }

/** Listem: eklenme sırasıyla afişler; tür ve izleme durumu süzgeci */
@Composable
fun ListemScreen() {
    val app = App.instance
    val actions = LocalActions.current
    val favs by app.user.favorites.collectAsStateWithLifecycle()
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val progress by app.user.progress.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(Kind.All) }
    var status by remember { mutableStateOf(Status.All) }

    val items = remember(favs, cat) { favs.mapNotNull { f -> if (f.kind == "movie") cat.movieById[f.itemId]?.item() else cat.seriesById[f.itemId]?.item() } }
    fun statusOf(i: Item): Status = when (i) {
        is Item.M -> progress.firstOrNull { it.key == i.key }.let { p -> when { p == null || p.positionMs < 15_000 -> Status.NotStarted; p.finished -> Status.Done; else -> Status.Partial } }
        is Item.S -> progress.filter { it.seriesId == i.s.id && !it.isUpNext }.let { ps -> when {
            ps.isEmpty() -> Status.NotStarted
            ps.maxByOrNull { it.updatedAt }!!.finished && progress.none { it.seriesId == i.s.id && it.isUpNext } -> Status.Done
            else -> Status.Partial
        } }
    }
    val byKind = items.filter { kind == Kind.All || (kind == Kind.Movies) == (it is Item.M) }
    val counts = Status.entries.associateWith { s -> if (s == Status.All) byKind.size else byKind.count { statusOf(it) == s } }
    val shown = byKind.filter { status == Status.All || statusOf(it) == status }
    val pmap by app.user.progressMap.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 100.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Listem", style = MaterialTheme.typography.displaySmall)
                val m = items.count { it is Item.M }; val s = items.size - m
                Text(listOfNotNull(if (m > 0) "$m film" else null, if (s > 0) "$s dizi" else null).joinToString(" · ").ifEmpty { " " }, style = MaterialTheme.typography.bodyMedium, color = C.muted)
                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    if (m > 0 && s > 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Kind.entries.forEach { k -> Chip(k.label, k == kind, { kind = k; status = Status.All }) } }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Status.entries.filter { it == Status.All || (counts[it] ?: 0) > 0 }.forEach { st -> Chip("${st.label} (${counts[st]})", st == status, { status = st }) }
                    }
                }
            }
        }
        if (items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState("Listen boş", "Bir film ya da dizinin sayfasında “Listem”e basınca burada görünür.")
        }
        items(shown, key = { it.key }) { i ->
            val p = pmap[i.key]
            PosterCard(i, onClick = { open(actions, i) }, width = 150.dp, progress = p?.fraction, watched = i is Item.M && p?.finished == true)
        }
    }
}
