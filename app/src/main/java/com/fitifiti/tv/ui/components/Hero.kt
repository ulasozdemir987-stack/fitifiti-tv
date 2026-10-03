package com.fitifiti.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.tmdb.Art
import com.fitifiti.tv.ui.theme.C
import java.util.concurrent.ConcurrentHashMap

/** Vitrin görseli: TMDB (anahtar varsa) → sağlayıcının sahne görseli → afiş (sağda) */
data class HeroArt(val backdrop: String?, val logo: String?, val overview: String?, val poster: String?, val vote: Double = 0.0, val votes: Int = 0, val tmdbId: Int? = null)

private val providerBackdrops = ConcurrentHashMap<String, String>()

@Composable
fun rememberArt(item: Item?): HeroArt {
    val app = App.instance
    var art by remember(item?.key) { mutableStateOf(initialArt(item)) }
    LaunchedEffect(item?.key) {
        if (item == null) return@LaunchedEffect
        val t: Art = when (item) {
            is Item.M -> app.art.art("movie", item.m.name, item.m.year, item.m.tmdb)
            is Item.S -> app.art.art("series", item.s.name, item.s.year)
        }
        var backdrop = t.backdrop ?: art.backdrop
        if (backdrop == null && item is Item.M) {
            backdrop = providerBackdrops[item.key] ?: runCatching { app.client().vodInfo(item.m.id).backdrop }.getOrNull()?.also { providerBackdrops[item.key] = it }
        }
        art = HeroArt(backdrop, t.logo, t.overview, item.image ?: t.poster, t.vote, t.votes, t.tmdbId)
    }
    return art
}

private fun initialArt(item: Item?): HeroArt {
    if (item == null) return HeroArt(null, null, null, null)
    val app = App.instance
    val cached = when (item) { is Item.M -> app.art.cached("movie", item.m.name, item.m.year, item.m.tmdb); is Item.S -> app.art.cached("series", item.s.name, item.s.year, null) }
    val provider = when (item) { is Item.M -> providerBackdrops[item.key]; is Item.S -> item.s.backdrop }
    return HeroArt(cached?.backdrop ?: provider, cached?.logo, cached?.overview, item.image, cached?.vote ?: 0.0, cached?.votes ?: 0, cached?.tmdbId)
}

/** Kenardan kenara arka plan: görsel sağa yaslı, soldan ve alttan zemine karışır (sitedeki .hero-media + .hero-fade) */
@Composable
fun HeroBackdrop(art: HeroArt, modifier: Modifier = Modifier, video: (@Composable BoxScope.() -> Unit)? = null) {
    Box(modifier.fillMaxSize()) {
        Crossfade(targetState = art.backdrop to art.poster, animationSpec = tween(700), label = "hero") { (bd, poster) ->
            Box(Modifier.fillMaxSize()) {
                if (bd != null) AsyncImage(model = bd, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight().align(Alignment.TopEnd))
                else if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.padding(top = 70.dp, end = 70.dp).width(250.dp).aspectRatio(2f / 3f).align(Alignment.TopEnd))
            }
        }
        video?.invoke(this)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.bg, 0.3f to C.bg.copy(alpha = 0.92f), 0.62f to C.bg.copy(alpha = 0.25f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to C.bg.copy(alpha = 0.55f), 0.18f to Color.Transparent, 0.62f to Color.Transparent, 1f to C.bg)))
    }
}

/** Logo varsa logo, yoksa Manrope başlık */
@Composable
fun HeroTitle(title: String, logo: String?, modifier: Modifier = Modifier, maxWidthFraction: Float = 0.45f, maxLogoHeight: androidx.compose.ui.unit.Dp = 130.dp) {
    if (logo != null) AsyncImage(model = logo, contentDescription = title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, modifier = modifier.fillMaxWidth(maxWidthFraction).heightIn(max = maxLogoHeight))
    else Text(title, style = MaterialTheme.typography.displayLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier.fillMaxWidth(0.6f))
}
