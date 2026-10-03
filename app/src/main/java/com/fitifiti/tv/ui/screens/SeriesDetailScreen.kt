package com.fitifiti.tv.ui.screens

import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.SubcomposeAsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ProgressEntity
import com.fitifiti.tv.data.tmdb.EpisodeArt
import com.fitifiti.tv.data.xtream.Episode
import com.fitifiti.tv.data.xtream.Series
import com.fitifiti.tv.data.xtream.SeriesInfo
import com.fitifiti.tv.domain.episodeName
import com.fitifiti.tv.domain.formatDuration
import com.fitifiti.tv.domain.splitTitle
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay

private sealed interface Load { data object Loading : Load; data class Error(val msg: String) : Load; data class Ready(val info: SeriesInfo) : Load }

/** Dizinin sıradaki bölümü: aynı sezonda bir sonraki, yoksa sonraki sezonun ilki */
fun nextEpisodeIn(seasons: Map<Int, List<Episode>>, ep: Episode): Episode? {
    val list = seasons[ep.season].orEmpty()
    val i = list.indexOfFirst { it.id == ep.id }
    if (i >= 0 && i + 1 < list.size) return list[i + 1]
    return seasons.keys.sorted().firstOrNull { it > ep.season }?.let { seasons[it]?.firstOrNull() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(s: Series, focusEpisodeId: String?) {
    val app = App.instance
    val actions = LocalActions.current
    val item = remember(s) { s.item() }
    val art = rememberArt(item)
    var variantTick by remember { mutableIntStateOf(0) }
    val variant = remember(s, variantTick) { actions.chosenVariant(item.key, s.variants) }
    val srcId = variant?.id ?: s.id
    var retry by remember { mutableIntStateOf(0) }
    val load by produceState<Load>(Load.Loading, srcId, retry) {
        value = Load.Loading
        value = try { Load.Ready(kotlinx.coroutines.withTimeout(30_000) { app.client().seriesInfo(srcId) }) } catch (e: Exception) { Load.Error(e.message ?: "Bölüm listesi yüklenemedi") }
    }
    val info = (load as? Load.Ready)?.info
    val seasons = info?.seasons.orEmpty()
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val favs by app.user.favorites.collectAsStateWithLifecycle()
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val fav = favs.any { it.key == item.key }
    val cast by produceState(emptyList<com.fitifiti.tv.data.tmdb.CastMember>(), art.tmdbId) { art.tmdbId?.let { value = app.art.cast("series", it) } }

    // Devam noktası: bu dizinin son kaydı; bittiyse sıradaki bölüm
    val seriesProgress = remember(progress, s.id) { progress.values.filter { it.seriesId == s.id }.maxByOrNull { it.updatedAt } }
    val target: Pair<Episode, ProgressEntity?>? = remember(seasons, seriesProgress) {
        if (seasons.isEmpty()) return@remember null
        val all = seasons.values.flatten()
        val last = seriesProgress?.let { p -> all.firstOrNull { it.id == p.itemId } ?: all.firstOrNull { it.season == p.season && it.num == p.episodeNum } }
        when {
            last == null -> all.first() to null
            seriesProgress!!.finished -> (nextEpisodeIn(seasons, last) ?: last) to null
            else -> last to seriesProgress
        }
    }
    var season by remember { mutableIntStateOf(-1) }
    LaunchedEffect(seasons.keys, target?.first?.season) {
        if (seasons.isEmpty()) return@LaunchedEffect
        val wanted = focusEpisodeId?.let { id -> seasons.values.flatten().firstOrNull { it.id == id }?.season }
        if (season !in seasons.keys) season = wanted ?: target?.first?.season ?: seasons.keys.first()
    }
    val tmdbEps by produceState(emptyMap<Int, EpisodeArt>(), art.tmdbId, season) { art.tmdbId?.let { id -> if (season > 0) value = app.art.season(id, season) } }
    val similar by produceState(emptyList<Item>(), s, cat) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { similarOf(cat.series, s, { it.categoryId }, { it.genre }, { it.rating }, { it.id }).map { it.item() } }
    }
    val (title, alt) = splitTitle(s.name)
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { playFocus.requestFocus() } }
    val h = LocalConfiguration.current.screenHeightDp

    DetailScaffold(art.copy(backdrop = art.backdrop ?: info?.backdrop ?: s.backdrop)) {
        item(key = "head") {
            Column(Modifier.heightIn(min = (h * 0.62f).dp).padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 18.dp), verticalArrangement = Arrangement.Bottom) {
                HeroTitle(title, art.logo, maxWidthFraction = 0.4f)
                if (alt.isNotBlank() && art.logo == null) { Spacer(Modifier.height(4.dp)); Text(alt, style = MaterialTheme.typography.titleMedium, color = C.muted) }
                Spacer(Modifier.height(14.dp))
                MetaRow(listOf(s.year ?: info?.releaseDate?.take(4),
                    (info?.genre ?: s.genre)?.split(',', '/', '&')?.take(3)?.joinToString(", ") { it.trim() },
                    if (seasons.isNotEmpty()) (if (seasons.size == 1) "${seasons.values.first().size} bölüm" else "${seasons.size} sezon") else null,
                    if (art.vote > 0 && art.votes >= 25) "TMDB ${"%.1f".format(art.vote)}" else null))
                Spacer(Modifier.height(12.dp))
                val overview = art.overview?.takeIf { it.isNotBlank() } ?: info?.plot ?: s.plot
                if (!overview.isNullOrBlank()) Text(overview, style = MaterialTheme.typography.bodyLarge, color = C.muted, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.55f))
                Spacer(Modifier.height(18.dp))
                VariantPicker(s.variants, variant) { v -> app.settings.chooseVariant(item.key, v.label); variantTick++; season = -1 }
                if (s.variants.size >= 2) Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val t = target
                    val label = when {
                        t == null -> if (load is Load.Loading) "Yükleniyor…" else "Oynat"
                        t.second != null -> "Devam et · ${t.first.num}. bölüm"
                        seriesProgress != null -> "Oynat · ${t.first.num}. bölüm"
                        else -> "Oynat"
                    }
                    Btn(label, { t?.let { actions.playEpisode(s, it.first, seasons, variant?.id) } }, Modifier.focusRequester(playFocus), icon = Icons.Default.PlayArrow)
                    Btn("Listem", { app.user.toggleFavorite(s) }, kind = BtnKind.Secondary, icon = if (fav) Icons.Default.Check else Icons.Default.Add)
                }
            }
        }
        item(key = "episodes") {
            Column(Modifier.padding(bottom = 26.dp)) {
                when (val l = load) {
                    is Load.Loading -> Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { repeat(4) { Skeleton(Modifier.width(300.dp).aspectRatio(16f / 9f)) } }
                    is Load.Error -> Column(Modifier.padding(horizontal = 48.dp)) {
                        Text("Bölüm listesi yüklenemedi", style = MaterialTheme.typography.titleMedium)
                        Text(l.msg, style = MaterialTheme.typography.bodySmall, color = C.muted)
                        Spacer(Modifier.height(10.dp))
                        Btn("Tekrar dene", { retry++ }, kind = BtnKind.Secondary)
                    }
                    is Load.Ready -> {
                        if (seasons.size > 1) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 14.dp)) {
                                    items(seasons.keys.toList()) { n -> Chip("$n. Sezon", n == season, { season = n }) }
                                }
                            }
                        } else SectionTitle("Bölümler")
                        val eps = seasons[season].orEmpty()
                        val rowState = rememberLazyListState()
                        LaunchedEffect(season, eps.size) {
                            val focusId = focusEpisodeId ?: target?.first?.id
                            val i = eps.indexOfFirst { it.id == focusId }
                            if (i > 0) rowState.scrollToItem(i)
                        }
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                            LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(eps.distinctBy { it.id }, key = { it.id }) { ep ->
                                    EpisodeCard(s, ep, tmdbEps[ep.num], progress["episode-${ep.id}"], art.backdrop ?: s.backdrop ?: s.cover) {
                                        actions.playEpisode(s, ep, seasons, variant?.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        castRow(cast)
        plainPosterRow("similar", "Buna benzer diziler", similar)
    }
}

/** Bölüm kartı: 16:9 sahne (TMDB still → sağlayıcı görseli → dizi görseli karartılmış + numara), "7 · Ad", süre, özet */
@Composable
private fun EpisodeCard(s: Series, ep: Episode, tmdb: EpisodeArt?, p: ProgressEntity?, fallback: String?, onClick: () -> Unit) {
    val name = tmdb?.name ?: episodeName(ep.title, s.name).takeIf { it.isNotBlank() && !it.matches(Regex("(?i).*bölüm\\s*\\d+.*|episode \\d+")) }
    val still = tmdb?.still ?: ep.image
    val dur = formatDuration(ep.durationSecs ?: tmdb?.runtime?.times(60)).ifBlank { null }
    val watched = p?.finished == true
    Column(Modifier.width(300.dp)) {
        Surface(
            onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).rememberFocus(),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.panel, focusedContainerColor = C.panel),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(10.dp))),
        ) {
            Box(Modifier.fillMaxSize()) {
                val numberFallback: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize()) {
                        if (fallback != null) SubcomposeAsyncImage(model = fallback, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.35f)
                        Text("${ep.num}", style = Display.copy(fontSize = 56.sp, color = Color(0xCCFFFFFF)), modifier = Modifier.align(Alignment.Center))
                    }
                }
                if (still != null) SubcomposeAsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), error = { numberFallback() }, loading = { Skeleton(Modifier.fillMaxSize(), 0.dp) })
                else numberFallback()
                if (watched) Text("✓ İzlendi", Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xBF000000)).padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.SemiBold)
                if (p != null && !watched && p.fraction > 0.01f) ProgressLine(p.fraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), track = Color(0x99000000))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(listOfNotNull("${ep.num}", name).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val overview = tmdb?.overview ?: ep.plot
        Text(listOfNotNull(dur, overview).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
