package com.fitifiti.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.tmdb.CastMember
import com.fitifiti.tv.domain.cardTitle
import com.fitifiti.tv.domain.episodeName
import com.fitifiti.tv.domain.formatDuration
import com.fitifiti.tv.ui.PlayRequest
import com.fitifiti.tv.ui.components.AnimatedBrandLogo
import com.fitifiti.tv.ui.components.IndeterminateLine
import com.fitifiti.tv.ui.components.KenBurns
import com.fitifiti.tv.ui.components.ProgressLine
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay

/** Oynatıcının bilgi katmanları için içerik bilgisi (sitedeki /api/player-meta'nın TV hali): Xtream + TMDB */
data class PlayerMeta(
    val logo: String? = null, val backdrop: String? = null, val overview: String? = null,
    val vote: Double = 0.0, val votes: Int = 0, val year: String? = null, val runtime: String? = null, val genres: String? = null,
    val cast: List<CastMember> = emptyList(), val epName: String? = null, val epOverview: String? = null, val epStill: String? = null,
)

@Composable
fun rememberPlayerMeta(req: PlayRequest): PlayerMeta {
    val app = App.instance
    val m = req.movie; val s = req.series; val e = req.episode
    val base = remember(req.key) {
        PlayerMeta(
            overview = m?.plot ?: s?.plot,
            year = (m?.year ?: s?.year)?.takeIf { it.isNotBlank() },
            runtime = m?.runtimeMin?.takeIf { it > 0 }?.let { formatDuration(it, "minutes") } ?: e?.durationSecs?.takeIf { it > 0 }?.let { formatDuration(it) },
            genres = (m?.genre ?: s?.genre)?.split(',', '/', '&')?.map { it.trim() }?.filter { it.isNotBlank() }?.take(2)?.joinToString(", ")?.ifBlank { null },
            epName = e?.let { episodeName(it.title, s?.name).ifBlank { null } }, epOverview = e?.plot?.takeIf { it.isNotBlank() }, epStill = e?.image,
        )
    }
    return produceState(base, req.key) {
        val kind = if (s != null) "series" else "movie"
        val title = m?.name ?: s?.name ?: req.title
        val art = runCatching { app.art.art(kind, title, m?.year ?: s?.year, m?.tmdb) }.getOrNull() ?: return@produceState
        value = value.copy(logo = art.logo, backdrop = art.backdrop, overview = art.overview?.takeIf { it.isNotBlank() } ?: value.overview, vote = art.vote, votes = art.votes)
        val id = art.tmdbId ?: return@produceState
        if (e != null) runCatching { app.art.season(id, e.season)[e.num] }.getOrNull()?.let { ep ->
            value = value.copy(epName = ep.name ?: value.epName, epOverview = ep.overview ?: value.epOverview, epStill = ep.still ?: value.epStill,
                runtime = value.runtime ?: ep.runtime?.let { formatDuration(it, "minutes") })
        }
        value = value.copy(cast = runCatching { app.art.cast(kind, id) }.getOrNull().orEmpty())
    }.value
}

/** "1. Sezon · 3. Bölüm · Ad" */
fun episodeLine(req: PlayRequest, meta: PlayerMeta): String? {
    val e = req.episode ?: return req.subtitle
    return listOfNotNull("${e.season}. Sezon", "${e.num}. Bölüm", meta.epName).joinToString(" · ")
}

/** Logo varsa logo, yoksa Manrope başlık */
@Composable
fun TitleArt(req: PlayRequest, meta: PlayerMeta, maxHeight: Dp, maxWidth: Dp, fontSize: Int) {
    val title = cardTitle(req.series?.name ?: req.movie?.name ?: req.title.substringBefore(" · "))
    var failed by remember(meta.logo) { mutableStateOf(false) }
    if (meta.logo != null && !failed) AsyncImage(
        model = meta.logo, contentDescription = title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
        modifier = Modifier.heightIn(max = maxHeight).widthIn(max = maxWidth), onError = { failed = true },
    ) else Text(title, style = Display.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.08f).sp), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = maxWidth))
}

@Composable
fun MetaLine(meta: PlayerMeta, color: Color = Color(0xCCFFFFFF)) {
    val parts = listOfNotNull(meta.year, if (meta.votes >= 25 && meta.vote > 0) "★ ${"%.1f".format(meta.vote)}" else null, meta.runtime, meta.genres)
    if (parts.isNotEmpty()) Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1)
}

/**
 * Açılış ekranı (sitedeki PlayerLoading): içerik seçildiği andan ilk kareye kadar tek ekran. Arkada içeriğin sahne
 * görseli yavaşça yakınlaşır, ortada kedili logo animasyonu (harflerin üstünden seker) + belirsiz çizgi + gerçek adım.
 */
@Composable
fun PlayerLoading(req: PlayRequest, meta: PlayerMeta) {
    var slow by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(9_000); slow = 1; delay(16_000); slow = 2 }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        (meta.backdrop ?: req.image)?.let { KenBurns(it, 0.28f) }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x99050508), C.bg), radius = 1400f)))
        Column(Modifier.align(Alignment.Center).offset(y = (-20).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedBrandLogo(320.dp)
            Spacer(Modifier.height(10.dp))
            IndeterminateLine(Modifier.width(220.dp))
            Spacer(Modifier.height(14.dp))
            Text(
                when (slow) { 0 -> "Görüntü hazırlanıyor…"; 1 -> "Sunucu biraz yavaş, az kaldı…"; else -> "Hâlâ bekleniyor · bağlantı sınırı dolu olabilir" },
                style = MaterialTheme.typography.bodyMedium, color = C.muted,
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 44.dp)) {
            TitleArt(req, meta, 64.dp, 300.dp, 26)
            episodeLine(req, meta)?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = C.muted) }
        }
    }
}

/**
 * Duraklatma ekranı + X-Ray (sitedeki pause-screen.tsx): soldan karartma, "İzliyorsun", logo, bölüm, yıl · puan · süre
 * · tür, özet ve oyuncular (fotoğraf + karakter). Oynatma sürene kadar kalır; kontroller üstüne gelir.
 */
@Composable
fun PauseScreen(visible: Boolean, req: PlayRequest, meta: PlayerMeta, compact: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(tween(450)), exit = fadeOut(tween(250))) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF2050508), 0.45f to Color(0xB3050508), 0.8f to Color(0x33050508), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x80000000), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x99000000))))
            run {
                Column(Modifier.padding(start = 56.dp, top = 40.dp).fillMaxWidth(0.5f)) {
                    Text("İzliyorsun", style = MaterialTheme.typography.labelLarge, color = C.muted)
                    Spacer(Modifier.height(10.dp))
                    TitleArt(req, meta, if (compact) 56.dp else 76.dp, 340.dp, if (compact) 28 else 34)
                    episodeLine(req, meta)?.let { Spacer(Modifier.height(10.dp)); Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(6.dp))
                    MetaLine(meta)
                    (meta.epOverview ?: meta.overview)?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xB3FFFFFF), maxLines = if (compact) 2 else 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (meta.cast.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("X-Ray", style = MaterialTheme.typography.labelLarge, color = C.primary)
                            Text("  ·  Oyuncular", style = MaterialTheme.typography.labelLarge, color = C.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { meta.cast.take(6).forEach { XRayPerson(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun XRayPerson(c: CastMember) {
    Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(C.fill3), contentAlignment = Alignment.Center) {
            Text(c.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }, style = Display.copy(fontSize = 18.sp, color = Color(0x99FFFFFF)))
            if (c.photo != null) AsyncImage(model = c.photo, contentDescription = c.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(c.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!c.role.isNullOrBlank()) Text(c.role, fontSize = 10.sp, color = C.faint, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Kaldığın yerden" ekranı (sitedeki ResumePrompt): sahne görseli, logo, bölüm, ilerleme, Devam et / Baştan başlat; 20 sn sonra kendiliğinden devam */
@Composable
fun ResumePrompt(req: PlayRequest, meta: PlayerMeta, onResume: () -> Unit, onRestart: () -> Unit) {
    val app = App.instance
    val progress by app.user.progressMap.collectAsStateWithLifecycle()
    val dur = progress[req.key]?.durationMs ?: 0L
    var left by remember { mutableIntStateOf(20) }
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { f.requestFocus() } }
    LaunchedEffect(left) { if (left <= 0) onResume() else { delay(1000); left-- } }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        (meta.epStill?.takeIf { req.episode != null } ?: meta.backdrop ?: req.image)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = 0.55f)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to C.bg, 0.5f to C.bg.copy(alpha = 0.75f), 1f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to C.bg)))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 52.dp).fillMaxWidth(0.5f)) {
            Text("Kaldığın yerden devam et", style = MaterialTheme.typography.labelLarge, color = C.muted)
            Spacer(Modifier.height(10.dp))
            TitleArt(req, meta, 76.dp, 340.dp, 34)
            episodeLine(req, meta)?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
            if (dur > 0) {
                ProgressLine(req.startMs.toFloat() / dur, Modifier.width(320.dp), height = 4.dp, track = C.fill3)
                Spacer(Modifier.height(8.dp))
                Text("${clock(req.startMs)}  ·  ${formatDuration((dur - req.startMs) / 1000)} kaldı", style = MaterialTheme.typography.bodySmall, color = C.muted)
            } else Text(clock(req.startMs), style = MaterialTheme.typography.bodySmall, color = C.muted)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column {
                    PillBtn("Devam et", onResume, Modifier.focusRequester(f), icon = Icons.Default.PlayArrow, primary = true)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.padding(horizontal = 14.dp).width(110.dp)) { ProgressLine(1f - left / 20f, Modifier.fillMaxWidth(), height = 2.dp, track = Color.Transparent) }
                }
                PillBtn("Baştan başlat", onRestart, icon = Icons.Default.Replay)
            }
        }
    }
}

/** İlerleme çubuğunun o noktadaki rengi (mor → turkuaz) */
fun progressColor(f: Float): Color = lerp(C.primary, C.teal, f.coerceIn(0f, 1f))

/** Sarma baloncuğu: tutamacın üstünde zaman */
@Composable
fun ScrubBubble(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}
