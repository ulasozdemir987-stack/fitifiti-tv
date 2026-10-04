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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

@Composable
fun PlatformScreen(brand: String) {
    val app = App.instance
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val cont by app.user.continueList.collectAsStateWithLifecycle()

    val (movies, series) = remember(cat, brand) {
        val validCatIds = cat.catName.entries.filter { categoryStyle(it.value).logo == brand }.map { it.key }.toSet()
        val m = cat.movies.filter { it.categoryId in validCatIds }
        val s = cat.series.filter { it.categoryId in validCatIds }
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
            "appletv" -> "Apple TV+"
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
    val heroItems = remember(rows, first) {
        val list = mutableListOf<Item>()
        if (first != null) list.add(first)
        list.addAll(rows.fs.take(4))
        list.addAll(rows.fm.take(4))
        list.distinctBy { it.key }.take(8)
    }

    val brandLogoRes = "file:///android_asset/brands/$brand.${if (brand == "exxen" || brand == "gain") "png" else "svg"}"
    val tint = remember(brand) { brandTint(brand) }

    var animState by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        animState = 1
        kotlinx.coroutines.delay(1000)
        animState = 2
    }

    val logoScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animState == 0) 0.85f else if (animState == 1) 1f else 1.25f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = if (animState == 0) 0 else if (animState == 1) 1000 else 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "logoScale"
    )
    val logoAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animState == 2) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(500),
        label = "logoAlpha"
    )
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (animState == 2) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(800, delayMillis = 200),
        label = "contentAlpha"
    )

    Box(Modifier.fillMaxSize().background(brandBg), contentAlignment = Alignment.Center) {
        if (contentAlpha < 1f) {
            AsyncImage(
                model = brandLogoRes, contentDescription = brandName,
                modifier = Modifier.height(140.dp).widthIn(max = 500.dp).scale(logoScale).alpha(logoAlpha),
                contentScale = ContentScale.Fit, colorFilter = tint
            )
        }

        if (animState == 2) {
            Box(Modifier.fillMaxSize().alpha(contentAlpha)) {
                HeroRowsLayout(
                    first, 
                    heroItems = heroItems, 
                    requestInitialFocus = true,
                    header = {
                        AsyncImage(model = brandLogoRes, contentDescription = brandName, modifier = Modifier.height(36.dp).widthIn(max = 180.dp), contentScale = ContentScale.Fit, colorFilter = tint)
                    }
                ) { onFocus ->
                    posterRow("fs", "$brandName Orijinal Dizileri", rows.fs, onFocus)
                    posterRow("fm", "$brandName Orijinal Filmleri", rows.fm, onFocus)
                    posterRow("ts", "En Beğenilen Diziler", rows.ts, onFocus, ranked = true)
                    posterRow("tm", "En Beğenilen Filmler", rows.tm, onFocus, ranked = true)
                    rows.gs.forEach { (g, list) -> posterRow("gs-$g", "$g Dizileri", list, onFocus) }
                    rows.gm.forEach { (g, list) -> posterRow("gm-$g", "$g Filmleri", list, onFocus) }
                }
            }
        }
    }
}
