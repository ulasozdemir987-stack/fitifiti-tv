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
import com.fitifiti.tv.data.catalog.Catalog
import com.fitifiti.tv.domain.Ranking
import com.fitifiti.tv.domain.categoryLabel
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*

/** Filmler / Diziler sekmesi: vitrin, devam et, öne çıkanlar, Keşfet (kategori kartları) ve kategori şeritleri */
@Composable
fun MediaScreen(kind: String) {
    val app = App.instance
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val cont by app.user.continueList.collectAsStateWithLifecycle()
    val rank = remember(cat) { Ranking.of(cat) }
    val movie = kind == "movie"
    val myCont = remember(cont) { cont.filter { (it.kind == "movie") == movie } }
    val cats = remember(cat, kind) { categoryItems(cat, kind) }
    val rows = remember(rank, kind) {
        object {
            val featured = if (movie) rank.featuredMovies.map { it.item() } else rank.featuredSeries.map { it.item() }
            val new = if (movie) rank.newMovies.map { it.item() } else rank.newSeries.map { it.item() }
            val top = if (movie) rank.topMovies.map { it.item() } else rank.topSeries.map { it.item() }
            val genres = rank.genreRows(kind, 4).map { (g, l) -> g to l.map { if (it is com.fitifiti.tv.data.xtream.Movie) it.item() else (it as com.fitifiti.tv.data.xtream.Series).item() } }
        }
    }
    val featured = rows.featured

    HeroRowsLayout(featured.firstOrNull()) { onFocus ->
        continueRow(myCont, onFocus)
        posterRow("feat", if (movie) "Öne çıkan filmler" else "Öne çıkan diziler", featured, onFocus)
        posterRow("new", "Yeni eklenenler", rows.new, onFocus)
        categoryRow(kind, cats)
        posterRow("top", "En beğenilenler", rows.top, onFocus, ranked = true)
        cats.take(10).forEach { (c, items) -> posterRow("c-${c.id}", categoryLabel(c.name), items.take(24), onFocus) }
        rows.genres.forEach { (g, list) -> posterRow("g-$g", g, list, onFocus) }
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
        Column(Modifier.padding(bottom = 22.dp)) {
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
