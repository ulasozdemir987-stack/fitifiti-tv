package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitifiti.tv.App
import com.fitifiti.tv.domain.Ranking
import com.fitifiti.tv.domain.categoryStyle
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment

@Composable
fun PlatformScreen(brand: String) {
    val app = App.instance
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val cont by app.user.continueList.collectAsStateWithLifecycle()

    val (movies, series) = remember(cat, brand) {
        val m = cat.movies.filter { categoryStyle(cat.catName[it.categoryId]).logo == brand }
        val s = cat.series.filter { categoryStyle(cat.catName[it.categoryId]).logo == brand }
        m to s
    }

    val brandName = remember(brand) {
        when(brand) {
            "netflix" -> "Netflix"
            "prime_video" -> "Prime Video"
            "disney_plus" -> "Disney+"
            "hbo" -> "HBO Max"
            "exxen" -> "Exxen"
            "blutv" -> "BluTV"
            "gain" -> "Gain"
            "tod" -> "TOD"
            "tabii" -> "tabii"
            else -> brand.replaceFirstChar { it.uppercase() }
        }
    }

    val brandBg = remember(brand) {
        when(brand) {
            "netflix" -> Color(0xFF000000)
            "prime_video" -> Color(0xFF0F171E)
            "disney_plus" -> Color(0xFF1A1D29)
            "hbo" -> Color(0xFF1C003D)
            else -> C.bg
        }
    }

    val rows = remember(movies, series) {
        val allMovies = movies.sortedByDescending { it.added }
        val allSeries = series.sortedByDescending { it.added }
        val topMovies = movies.filter { it.rating > 0 }.sortedByDescending { it.rating }.take(20)
        val topSeries = series.filter { it.rating > 0 }.sortedByDescending { it.rating }.take(20)
        object {
            val fm = allMovies.take(20).map { it.item() }
            val fs = allSeries.take(20).map { it.item() }
            val tm = topMovies.map { it.item() }
            val ts = topSeries.map { it.item() }
            val gm = Ranking.splitGenres("Aksiyon, Dram, Komedi, Bilim Kurgu, Korku, Gerilim").mapNotNull { g ->
                val l = allMovies.filter { Ranking.splitGenres(it.genre).contains(g) }.take(20).map { it.item() }
                if (l.size >= 3) g to l else null
            }
            val gs = Ranking.splitGenres("Dram, Komedi, Suç, Bilim Kurgu, Belgesel, Aksiyon").mapNotNull { g ->
                val l = allSeries.filter { Ranking.splitGenres(it.genre).contains(g) }.take(20).map { it.item() }
                if (l.size >= 3) g to l else null
            }
        }
    }

    val first = remember(rows) { rows.fs.firstOrNull() ?: rows.fm.firstOrNull() }
    val brandLogoRes = "file:///android_asset/brands/$brand.${if (brand == "exxen" || brand == "gain") "png" else "svg"}"

    Box(Modifier.fillMaxSize().background(brandBg)) {
        HeroRowsLayout(first, requestInitialFocus = true) { onFocus ->
            item {
                Row(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = brandLogoRes, contentDescription = brandName, modifier = Modifier.height(48.dp).widthIn(max = 200.dp), contentScale = ContentScale.Fit)
                }
            }
            posterRow("fs", "$brandName Orijinal Dizileri", rows.fs, onFocus)
            posterRow("fm", "$brandName Orijinal Filmleri", rows.fm, onFocus)
            posterRow("ts", "En Beğenilen Diziler", rows.ts, onFocus, ranked = true)
            posterRow("tm", "En Beğenilen Filmler", rows.tm, onFocus, ranked = true)
            rows.gs.forEach { (g, list) -> posterRow("gs-$g", "$g Dizileri", list, onFocus) }
            rows.gm.forEach { (g, list) -> posterRow("gm-$g", "$g Filmleri", list, onFocus) }
        }
    }
}
