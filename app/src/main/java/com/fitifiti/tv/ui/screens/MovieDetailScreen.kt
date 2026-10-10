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
    val critics by produceState<com.fitifiti.tv.data.tmdb.Critics?>(null, art.tmdbId) { art.tmdbId?.let { value = app.art.critics("movie", it) } }
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

    DetailScaffold(art.copy(backdrop = art.backdrop ?: info.backdrop), TrailerSpec("movie", m.name, m.year, info.trailer)) {
        item(key = "head") {
            // Başlık bloğu sol üstten başlar (alta yaslıyken logo ekranın ortasına iniyor, özet kesiliyordu): logo ≤72 dp, özet 4 satır,
            // ikincil eylemler yazısız yuvarlak düğmeler; bilgi listesi ayrı öğede
            // JetStream tasarım dili: ferah sol üst başlangıç, nefes alan tipografi ve hiyerarşi
            Column(
                Modifier
                    .detailHead()
                    .padding(start = 48.dp, end = 54.dp, top = 40.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroTitle(title, art.logo, maxWidthFraction = 0.36f, maxLogoHeight = 78.dp)
                if (alt.isNotBlank() && art.logo == null) {
                    Text(alt, style = MaterialTheme.typography.titleMedium, color = C.muted)
                }
                MetaRow(listOf(
                    m.year ?: info.releaseDate?.take(4),
                    formatDuration(info.durationSecs ?: m.runtimeMin?.times(60)).ifBlank { null },
                    com.fitifiti.tv.domain.formatGenres(info.genre ?: m.genre, 3),
                    info.age?.takeIf { it.isNotBlank() && it != "0" }?.let { "$it+" }?.replace("++", "+"),
                    if (art.vote > 0 && art.votes >= 25 && critics?.imdb == null) "TMDB ${"%.1f".format(art.vote)}" else null
                ))
                val wikiAwards by produceState(emptyList<com.fitifiti.tv.data.tmdb.AwardCount>(), critics?.imdbId) { critics?.imdbId?.let { value = app.art.awards(it) } }
                val laurels = remember(critics, wikiAwards) { laurelBadges(critics, wikiAwards) }
                CriticsRow(critics)
                AwardLaurels(laurels)
                val overview = art.overview?.takeIf { it.isNotBlank() } ?: info.plot ?: m.plot
                if (!overview.isNullOrBlank()) {
                    Text(overview, style = MaterialTheme.typography.bodyMedium, color = C.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.52f))
                }

                if (resume) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(Modifier.width(260.dp)) { ProgressLine(p!!.fraction, Modifier.fillMaxWidth(), track = C.fill3) }
                        Spacer(Modifier.width(12.dp))
                        Text(formatDuration((p!!.durationMs - p.positionMs) / 1000) + " kaldı", style = MaterialTheme.typography.bodySmall, color = C.muted)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Btn(if (resume) "Devam et" else "Oynat", { actions.playMovie(m) }, Modifier.focusRequester(playFocus), icon = Icons.Default.PlayArrow)
                    if (resume || watched) IconAction(Icons.Default.Replay, "Baştan oynat", { actions.playMovie(m, fromStart = true) })
                    IconAction(if (fav) Icons.Default.Check else Icons.Default.Add, if (fav) "Listemden çıkar" else "Listeme ekle", { app.user.toggleFavorite(m) }, active = fav)
                    IconAction(Icons.Default.DoneAll, if (watched) "İzlenmedi yap" else "İzlendi olarak işaretle", { app.user.markMovieWatched(m, !watched) }, active = watched)
                    if (m.variants.size >= 2) {
                        VariantPicker(m.variants, variant) { v -> app.settings.chooseVariant(item.key, v.label); variantTick++ }
                    }
                    TrailerMuteButton()
                }
            }
        }
        item(key = "info") {
            InfoList(listOf("Yönetmen" to info.director, "Oyuncular" to (if (cast.isEmpty()) info.cast else null), "Ülke" to info.country), Modifier.padding(start = 48.dp, bottom = 24.dp).fillMaxWidth(0.6f))
        }
        castRow(cast)
        plainPosterRow("similar", "Benzer filmler", similar)
    }
}
