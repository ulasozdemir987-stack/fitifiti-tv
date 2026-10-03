package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.VodInfo
import com.fitifiti.tv.domain.formatDuration
import com.fitifiti.tv.domain.splitTitle
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay

@Composable
fun MovieDetailScreen(m: Movie) {
    val app = App.instance
    val actions = LocalActions.current
    val item = remember(m) { m.item() }
    val art = rememberArt(item)
    val info by produceState(VodInfo(), m.id) { value = runCatching { app.client().vodInfo(m.id) }.getOrDefault(VodInfo()) }
    val cast by produceState(emptyList<com.fitifiti.tv.data.tmdb.CastMember>(), art.tmdbId) { art.tmdbId?.let { value = app.art.cast("movie", it) } }
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val favs by app.user.favorites.collectAsStateWithLifecycle()
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    var variantTick by remember { mutableIntStateOf(0) }
    val variant = remember(m, variantTick) { actions.chosenVariant(item.key, m.variants) }
    val p = progress[item.key]
    val resume = p != null && !p.finished && p.positionMs > 15_000
    val watched = p?.finished == true
    val fav = favs.any { it.key == item.key }
    // Tüm katalog taranır: ana iş parçacığında yapılırsa TV donuyor
    val similar by produceState(emptyList<Item>(), m, cat) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { similarOf(cat.movies, m, { it.categoryId }, { it.genre }, { it.rating }, { it.id }).map { it.item() } }
    }
    val (title, alt) = splitTitle(m.name)
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { playFocus.requestFocus() } }
    val h = LocalConfiguration.current.screenHeightDp

    DetailScaffold(art.copy(backdrop = art.backdrop ?: info.backdrop)) {
        item(key = "head") {
            Column(Modifier.heightIn(min = (h * 0.9f).dp).padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 24.dp), verticalArrangement = Arrangement.Bottom) {
                HeroTitle(title, art.logo, maxWidthFraction = 0.4f)
                if (alt.isNotBlank() && art.logo == null) { Spacer(Modifier.height(4.dp)); Text(alt, style = MaterialTheme.typography.titleMedium, color = C.muted) }
                Spacer(Modifier.height(14.dp))
                MetaRow(listOf(m.year ?: info.releaseDate?.take(4),
                    formatDuration(info.durationSecs ?: m.runtimeMin?.times(60)).ifBlank { null },
                    (info.genre ?: m.genre)?.split(',', '/', '&')?.take(3)?.joinToString(", ") { it.trim() },
                    info.age?.takeIf { it.isNotBlank() && it != "0" }?.let { "$it+" }?.replace("++", "+"),
                    if (art.vote > 0 && art.votes >= 25) "TMDB ${"%.1f".format(art.vote)}" else null))
                Spacer(Modifier.height(12.dp))
                val overview = art.overview?.takeIf { it.isNotBlank() } ?: info.plot ?: m.plot
                if (!overview.isNullOrBlank()) Text(overview, style = MaterialTheme.typography.bodyLarge, color = C.muted, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.55f))
                Spacer(Modifier.height(18.dp))
                VariantPicker(m.variants, variant) { v -> app.settings.chooseVariant(item.key, v.label); variantTick++ }
                if (m.variants.size >= 2) Spacer(Modifier.height(14.dp))
                if (resume) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.width(260.dp)) { ProgressLine(p!!.fraction, Modifier.fillMaxWidth(), track = C.fill3) }
                        Spacer(Modifier.width(12.dp))
                        Text(formatDuration((p!!.durationMs - p.positionMs) / 1000) + " kaldı", style = MaterialTheme.typography.bodySmall, color = C.muted)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn(if (resume) "Devam et" else "Oynat", { actions.playMovie(m) }, Modifier.focusRequester(playFocus), icon = Icons.Default.PlayArrow)
                    if (resume || watched) Btn("Baştan oynat", { actions.playMovie(m, fromStart = true) }, kind = BtnKind.Secondary, icon = Icons.Default.Replay)
                    Btn("Listem", { app.user.toggleFavorite(m) }, kind = BtnKind.Secondary, icon = if (fav) Icons.Default.Check else Icons.Default.Add)
                    Btn(if (watched) "İzlenmedi yap" else "İzlendi", { app.user.markMovieWatched(m, !watched) }, kind = BtnKind.Ghost, icon = Icons.Default.DoneAll)
                }
                Spacer(Modifier.height(22.dp))
                InfoList(listOf("Yönetmen" to info.director, "Oyuncular" to (if (cast.isEmpty()) info.cast else null), "Ülke" to info.country), Modifier.fillMaxWidth(0.6f))
            }
        }
        castRow(cast)
        plainPosterRow("similar", "Benzer filmler", similar)
    }
}
