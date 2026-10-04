package com.fitifiti.tv.ui.player

import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C as MC
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ProgressEntity
import com.fitifiti.tv.data.xtream.Episode
import com.fitifiti.tv.domain.cardTitle
import com.fitifiti.tv.domain.episodeName
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.PlayRequest
import com.fitifiti.tv.ui.components.ProgressLine
import com.fitifiti.tv.ui.components.Wordmark
import com.fitifiti.tv.ui.components.MascotLoader
import com.fitifiti.tv.ui.components.IndeterminateLine
import com.fitifiti.tv.ui.components.KenBurns
import com.fitifiti.tv.ui.screens.nextEpisodeIn
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class Panel { None, Tracks, Episodes, Sleep }
private sealed interface Sleep { data class At(val endAt: Long, val label: String) : Sleep; data object EpisodeEnd : Sleep }

@Composable
fun PlayerScreen(req: PlayRequest, onClose: () -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val actions = LocalActions.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val player = remember(req.url) { buildPlayer(ctx, live = false) }

    var askResume by remember(req.url) { mutableStateOf(req.askResume) }
    var pos by remember { mutableLongStateOf(req.startMs) }
    var dur by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(Panel.None) }
    var scrub by remember { mutableStateOf<Long?>(null) }
    var scrubAt by remember { mutableLongStateOf(0L) }
    var sleep by remember { mutableStateOf<Sleep?>(null) }
    var sleeping by remember { mutableStateOf(false) }
    var nextDismissed by remember { mutableStateOf(false) }
    var nextCountdown by remember { mutableIntStateOf(-1) }
    var upNextSaved by remember { mutableStateOf(false) }
    var tracksTick by remember { mutableIntStateOf(0) }
    var xray by remember { mutableStateOf(false) }
    var pausedLong by remember { mutableStateOf(false) }
    var pairOpen by remember { mutableStateOf(false) }
    val meta = rememberPlayerMeta(req)

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }

    // Bölüm listesi (sonraki bölüm + bölüm paneli için); yoksa sağlayıcıdan
    val seasons by produceState(req.seasons, req.url) {
        if (value == null && req.series != null) value = runCatching { app.client().seriesInfo(req.variantSeriesId ?: req.series.id).seasons }.getOrNull()
    }
    val episode: Episode? = remember(seasons, req) { req.episode?.let { e -> seasons?.values?.flatten()?.firstOrNull { it.id == e.id } ?: e } }
    val next: Episode? = remember(seasons, episode) { if (seasons != null && episode != null) nextEpisodeIn(seasons!!, episode) else null }

    // Dinleyici/döngü ilk bileşimde kurulduğu için güncel değerler bu referanslardan okunur
    val nextRef = rememberUpdatedState(next)
    val epRef = rememberUpdatedState(episode)
    val setRef = rememberUpdatedState(settings)
    val seasonsRef = rememberUpdatedState(seasons)

    fun touch() { lastInput = System.currentTimeMillis() }
    // Kontroller görünür olduktan sonra odaklanacak öğe (henüz çizilmemiş öğeye odak verilemez)
    var pendingFocus by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(controls, pendingFocus) {
        val f = pendingFocus ?: return@LaunchedEffect
        if (!controls) return@LaunchedEffect
        repeat(5) { delay(40); if (runCatching { f.requestFocus() }.isSuccess) { pendingFocus = null; return@LaunchedEffect } }
        pendingFocus = null
    }
    fun showControls(focus: FocusRequester? = null) {
        touch()
        if (!controls) { controls = true }
        if (focus != null) pendingFocus = focus
    }

    fun saveProgress() {
        val p = player.currentPosition.takeIf { started } ?: return
        val d = player.duration.takeIf { it > 0 && it != MC.TIME_UNSET } ?: return
        if (p < 5_000) return
        app.user.saveProgress(ProgressEntity(
            profileId = 0, key = req.key, kind = req.kind, itemId = req.itemId, seriesId = req.series?.id, season = epRef.value?.season, episodeNum = epRef.value?.num,
            title = req.title, subtitle = req.subtitle, image = req.image, ext = req.ext, positionMs = p, durationMs = d,
        ))
        val n = nextRef.value
        if (req.kind == "episode" && p.toFloat() / d >= 0.96f && !upNextSaved && n != null && req.series != null) {
            upNextSaved = true
            app.user.addUpNext(req.series, n, req.image)
        }
    }

    fun playNext() {
        val s = req.series ?: return
        val n = nextRef.value ?: return
        saveProgress()
        actions.playEpisode(s, n, seasonsRef.value, req.variantSeriesId, fromStart = true, replace = true)
    }

    // Oynatıcı yaşam döngüsü
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE && error == null
                if (state == Player.STATE_READY) { started = true; error = null }
                if (state == Player.STATE_ENDED) {
                    saveProgress()
                    if (sleep == Sleep.EpisodeEnd) { sleeping = true; sleep = null }
                    else if (nextRef.value != null && setRef.value.autoNext) { nextDismissed = false; if (nextCountdown < 0) nextCountdown = 10 }
                    else if (nextRef.value == null) onClose()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying; if (!isPlaying) saveProgress() }
            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sağlayıcı yayını vermedi (bağlantı sınırı dolu olabilir)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sunucuya bağlanılamadı."
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Bu cihaz videonun biçimini çözemiyor."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Dosya biçimi okunamadı."
                    else -> "Video oynatılamadı."
                } + " (${e.errorCodeName})"
                loading = false
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { tracksTick++ }
        }
        player.addListener(listener)
        player.setMediaItem(mediaItem(req.url), if (req.askResume) 0 else req.startMs)
        if (req.askResume) player.seekTo(req.startMs)
        player.playWhenReady = !req.askResume
        player.prepare()
        onDispose { saveProgress(); player.removeListener(listener); player.release() }
    }

    // Uygulama arka plana geçince duraklat
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { player.pause(); saveProgress() } }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }

    // Zaman, otomatik gizleme, kayıt, uyku
    LaunchedEffect(player) {
        var lastSave = 0L
        while (isActive) {
            pos = player.currentPosition
            dur = player.duration.takeIf { it > 0 && it != MC.TIME_UNSET } ?: 0
            buffered = player.bufferedPosition
            val now = System.currentTimeMillis()
            if (controls && (playing || pausedLong || xray) && panel == Panel.None && scrub == null && now - lastInput > (if (playing) 4_500 else 6_000)) { controls = false; runCatching { rootFocus.requestFocus() } }
            if (now - lastSave > 10_000 && playing) { lastSave = now; saveProgress() }
            (sleep as? Sleep.At)?.let { s ->
                val left = s.endAt - now
                if (left in 1..10_000) player.volume = left / 10_000f
                if (left <= 0) { player.pause(); player.volume = 1f; sleep = null; sleeping = true }
            }
            // Jeneriğe girince sonraki bölüm kartı
            if (nextRef.value != null && setRef.value.autoNext && dur > 5 * 60_000 && dur - pos in 1..15_000 && !nextDismissed && nextCountdown < 0 && sleep != Sleep.EpisodeEnd) nextCountdown = 10
            delay(500)
        }
    }
    LaunchedEffect(nextCountdown) {
        if (nextCountdown < 0) return@LaunchedEffect
        if (nextCountdown == 10) runCatching { nextFocus.requestFocus() }
        if (nextCountdown == 0) { playNext(); return@LaunchedEffect }
        delay(1000)
        if (nextCountdown > 0) nextCountdown--
    }
    // Sarma: tuşlar bırakıldıktan 0,7 sn sonra uygula
    LaunchedEffect(scrub, scrubAt) {
        val target = scrub ?: return@LaunchedEffect
        delay(700)
        player.seekTo(target); pos = target; scrub = null; touch()
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    // Duraklatınca 0,6 sn sonra duraklatma ekranı (oynatma sürene kadar kalır); oynayınca X-Ray de kapanır
    LaunchedEffect(playing, started, scrub) {
        if (playing) { pausedLong = false; xray = false; return@LaunchedEffect }
        if (!started || scrub != null) return@LaunchedEffect
        delay(600); pausedLong = true
    }
    // Telefon kumandası: oynatılan içerik telefonda görünür, telefondaki çubukla sarılır
    DisposableEffect(Unit) {
        com.fitifiti.tv.data.remote.RemoteBus.screen.value = "player"
        onDispose { com.fitifiti.tv.data.remote.RemoteBus.player.value = null; com.fitifiti.tv.data.remote.RemoteBus.screen.value = "app" }
    }
    LaunchedEffect(playing, dur / 1000, scrub) {
        com.fitifiti.tv.data.remote.RemoteBus.player.value = com.fitifiti.tv.data.remote.RemotePlayer(cardTitle(req.title), req.subtitle, req.image, player.currentPosition / 1000, dur / 1000, playing)
    }
    LaunchedEffect(Unit) { com.fitifiti.tv.data.remote.RemoteBus.seek.collect { s -> player.seekTo(s * 1000); touch(); showControls() } }
    // Çekmece kapanınca odak oynat düğmesine döner
    LaunchedEffect(panel) { if (panel == Panel.None && controls) { touch(); pendingFocus = playFocus } }

    // Konum her yarım saniyede değişir; tüm ekran yalnız "Girişi atla" görünürlüğü değişince yeniden çizilsin
    val introVisible by remember { derivedStateOf { settings.skipIntro && req.kind == "episode" && started && !askResume && pos in 5_000..240_000 && (dur == 0L || dur > 10 * 60_000) } }
    LaunchedEffect(introVisible) { if (introVisible && !controls) { delay(100); runCatching { skipFocus.requestFocus() } } }

    fun stepSeek(forward: Boolean, repeat: Int) {
        val step = (10_000L * (1 + repeat / 4)).coerceAtMost(120_000L)
        val base = scrub ?: player.currentPosition
        scrub = (base + if (forward) step else -step).coerceIn(0, if (dur > 0) dur - 1_000 else Long.MAX_VALUE)
        scrubAt = System.currentTimeMillis()
        showControls()
    }

    BackHandler {
        when {
            sleeping -> { sleeping = false; onClose() }
            askResume -> onClose()
            panel != Panel.None -> panel = Panel.None
            nextCountdown >= 0 -> { nextCountdown = -1; nextDismissed = true; runCatching { rootFocus.requestFocus() } }
            controls -> { controls = false; runCatching { rootFocus.requestFocus() } }
            xray -> xray = false
            else -> onClose()
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch()
                val code = e.key.nativeKeyCode
                when (code) {
                    AKey.KEYCODE_MEDIA_PLAY_PAUSE, AKey.KEYCODE_SPACE -> { if (player.isPlaying) player.pause() else player.play(); showControls(if (controls) null else playFocus); return@onPreviewKeyEvent true }
                    AKey.KEYCODE_MEDIA_PLAY -> { player.play(); return@onPreviewKeyEvent true }
                    AKey.KEYCODE_MEDIA_PAUSE -> { player.pause(); showControls(playFocus); return@onPreviewKeyEvent true }
                    AKey.KEYCODE_MEDIA_FAST_FORWARD -> { stepSeek(true, e.nativeKeyEvent.repeatCount); return@onPreviewKeyEvent true }
                    AKey.KEYCODE_MEDIA_REWIND -> { stepSeek(false, e.nativeKeyEvent.repeatCount); return@onPreviewKeyEvent true }
                    AKey.KEYCODE_MEDIA_NEXT -> { if (next != null) playNext(); return@onPreviewKeyEvent true }
                    AKey.KEYCODE_CAPTIONS -> { controls = true; panel = Panel.Tracks; return@onPreviewKeyEvent true }
                }
                if (askResume || sleeping || error != null || panel != Panel.None || nextCountdown >= 0) return@onPreviewKeyEvent false
                if (!controls) {
                    when (code) {
                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> {
                            if (introVisible) { player.seekTo(player.currentPosition + 85_000); return@onPreviewKeyEvent true }
                            if (player.isPlaying) { player.pause(); showControls(playFocus) } else player.play()
                            true
                        }
                        AKey.KEYCODE_DPAD_LEFT -> { stepSeek(false, e.nativeKeyEvent.repeatCount); pendingFocus = seekFocus; true }
                        AKey.KEYCODE_DPAD_RIGHT -> { stepSeek(true, e.nativeKeyEvent.repeatCount); pendingFocus = seekFocus; true }
                        // ▲ = X-Ray (Prime Video gibi): duraklatıp oyuncuları ve bilgiyi gösterir; tekrar ▲ kapatır
                        AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_INFO -> { if (xray) xray = false else { xray = true; player.pause() }; true }
                        AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_MENU -> { showControls(playFocus); true }
                        else -> false
                    }
                } else false
            },
    ) {
        VideoSurface(player, Modifier.fillMaxSize(), settings.subtitleScale)

        // Duraklatma ekranı + X-Ray (kontrollerin altında)
        PauseScreen((pausedLong || xray) && started && error == null && !askResume && !sleeping && panel == Panel.None && nextCountdown < 0, req, meta, compact = controls)

        // Yükleniyor
        if (!started && error == null && !askResume) PlayerLoading(req, meta)
        else if (loading && error == null && !pausedLong) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }

        // Kontroller (sitedeki gibi çerçevesiz, büyük; başlık alt çubuğun ortasında)
        AnimatedVisibility(controls && error == null && !askResume && !sleeping, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.18f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xE6000000))))
                // Üst: küçük logo · saat ve bitiş saati · uyku
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 56.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!(pausedLong || xray)) com.fitifiti.tv.ui.components.BrandLogo(18)
                    Spacer(Modifier.weight(1f))
                    val sl = sleep
                    if (sl is Sleep.At) Text("Uyku · ${((sl.endAt - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)} dk", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 18.dp))
                    if (sl == Sleep.EpisodeEnd) Text("Uyku · bitince", color = Color(0xFFC4B5FD), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 18.dp))
                    val endsAt = if (dur > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr")).format(java.util.Date(System.currentTimeMillis() + (dur - pos).coerceAtLeast(0))) else null
                    Text(listOfNotNull(java.text.SimpleDateFormat("HH:mm", java.util.Locale("tr")).format(java.util.Date()), endsAt?.let { "Bitiş $it" }).joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = Color(0xB3FFFFFF))
                }

                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 56.dp, end = 56.dp, bottom = 34.dp)) {
                    // İlerleme (konum yalnız bu bölümde okunur → saniyede iki kez yalnız burası çizilir)
                    val displayPos = scrub ?: pos
                    val src = remember { MutableInteractionSource() }
                    val seekFocused by src.collectIsFocusedAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).focusRequester(seekFocus).focusable(interactionSource = src)
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key.nativeKeyCode) {
                                        AKey.KEYCODE_DPAD_LEFT -> { stepSeek(false, e.nativeKeyEvent.repeatCount); true }
                                        AKey.KEYCODE_DPAD_RIGHT -> { stepSeek(true, e.nativeKeyEvent.repeatCount); true }
                                        AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER -> { scrub?.let { player.seekTo(it); scrub = null } ?: run { if (player.isPlaying) player.pause() else player.play() }; true }
                                        else -> false
                                    }
                                },
                        ) { SeekBar(if (dur > 0) displayPos.toFloat() / dur else 0f, if (dur > 0) buffered.toFloat() / dur else 0f, seekFocused, bubble = scrub?.let { clock(it) }) }
                        Text("-" + clock((dur - displayPos).coerceAtLeast(0)), style = MaterialTheme.typography.bodyMedium, color = Color(0xD9FFFFFF), modifier = Modifier.padding(start = 16.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(70.dp)) {
                        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CtrlBtn(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Duraklat" else "Oynat", { touch(); if (player.isPlaying) player.pause() else player.play() }, Modifier.focusRequester(playFocus), size = 56.dp)
                            CtrlBtn(Icons.Default.Replay10, "10 sn geri", { touch(); player.seekBack() })
                            CtrlBtn(Icons.Default.Forward10, "10 sn ileri", { touch(); player.seekForward() })
                        }
                        Column(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).widthIn(max = 300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(cardTitle(req.series?.name ?: req.movie?.name ?: req.title.substringBefore(" · ")), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            req.episode?.let { e -> Text(listOfNotNull("S${e.season} · B${e.num}", meta.epName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CtrlBtn(Icons.Default.PersonSearch, "X-Ray", { touch(); if (xray) xray = false else { xray = true; player.pause() } }, active = xray)
                            if (seasons != null && req.kind == "episode") CtrlBtn(Icons.Default.VideoLibrary, "Bölümler", { panel = Panel.Episodes })
                            CtrlBtn(Icons.Default.Subtitles, "Ses ve altyazı", { panel = Panel.Tracks })
                            CtrlBtn(Icons.Default.Bedtime, "Uyku", { panel = Panel.Sleep }, active = sleep != null)
                            CtrlBtn(Icons.Default.PhoneAndroid, "Telefonla kumanda", { pairOpen = true })
                            if (next != null) CtrlBtn(Icons.Default.SkipNext, "Sonraki bölüm", { playNext() })
                        }
                    }
                }
            }
        }
        if (pairOpen) com.fitifiti.tv.ui.components.RemotePairDialog { pairOpen = false; touch() }


        // Girişi atla
        if (introVisible && !controls && nextCountdown < 0) Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomEnd) {
            PillBtn("Girişi atla", { player.seekTo(player.currentPosition + 85_000); runCatching { rootFocus.requestFocus() } }, Modifier.focusRequester(skipFocus), icon = Icons.Default.FastForward)
        }

        // Sonraki bölüm kartı
        if (nextCountdown >= 0 && next != null) NextEpisodeCard(next, req, nextCountdown, nextFocus, onPlay = { playNext() }, onDismiss = {
            nextCountdown = -1; nextDismissed = true; runCatching { rootFocus.requestFocus() }
            if (player.playbackState == Player.STATE_ENDED) onClose()
        })

        // Çekmeceler
        SidePanel(panel == Panel.Tracks, "Ses ve altyazı", width = 520.dp) {
            val audio = remember(tracksTick) { tracksOf(player, MC.TRACK_TYPE_AUDIO) }
            val text = remember(tracksTick) { tracksOf(player, MC.TRACK_TYPE_TEXT) }
            val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(MC.TRACK_TYPE_TEXT) || text.none { it.selected }
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LazyColumn(Modifier.weight(1f)) {
                    item { Text("Ses", style = MaterialTheme.typography.labelLarge, color = C.muted, modifier = Modifier.padding(bottom = 6.dp)) }
                    if (audio.isEmpty()) item { Text("Tek ses", color = C.faint, modifier = Modifier.padding(14.dp)) }
                    itemsIndexed(audio) { i, o -> OptionRow(o.label, o.selected, { selectTrack(player, MC.TRACK_TYPE_AUDIO, o); tracksTick++ }, if (i == 0) Modifier.focusRequester(first) else Modifier) }
                }
                LazyColumn(Modifier.weight(1f)) {
                    item { Text("Altyazı", style = MaterialTheme.typography.labelLarge, color = C.muted, modifier = Modifier.padding(bottom = 6.dp)) }
                    item { OptionRow("Kapalı", textOff, { selectTrack(player, MC.TRACK_TYPE_TEXT, null); tracksTick++ }, if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier) }
                    items(text) { o -> OptionRow(o.label, o.selected && !textOff, { selectTrack(player, MC.TRACK_TYPE_TEXT, o); tracksTick++ }) }
                    if (text.isEmpty()) item { Text("Bu videoda altyazı yok", color = C.faint, fontSize = 13.sp, modifier = Modifier.padding(14.dp)) }
                }
            }
        }
        SidePanel(panel == Panel.Episodes, req.series?.let { cardTitle(it.name) } ?: "Bölümler", width = 560.dp) {
            val ss = seasons.orEmpty()
            var season by remember { mutableIntStateOf(episode?.season ?: ss.keys.firstOrNull() ?: 1) }
            val progress by app.user.progressMap.collectAsStateWithLifecycle()
            val list = ss[season].orEmpty()
            val state = rememberLazyListState()
            val cur = remember { FocusRequester() }
            LaunchedEffect(season) {
                val i = list.indexOfFirst { it.id == episode?.id }.coerceAtLeast(0)
                state.scrollToItem(i); delay(80); runCatching { cur.requestFocus() }
            }
            if (ss.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                ss.keys.take(8).forEach { n -> com.fitifiti.tv.ui.components.Chip("$n. Sezon", n == season, { season = n }) }
            }
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(list, key = { i, e -> "$i-${e.id}" }) { i, e ->
                    val p = progress["episode-${e.id}"]
                    val isCur = e.id == episode?.id
                    OptionRow(
                        "${e.num} · " + (episodeName(e.title, req.series?.name).ifBlank { "Bölüm ${e.num}" }), isCur,
                        { if (!isCur && req.series != null) { saveProgress(); actions.playEpisode(req.series, e, seasons, req.variantSeriesId, replace = true) } else panel = Panel.None },
                        if (isCur || (i == 0 && list.none { it.id == episode?.id })) Modifier.focusRequester(cur) else Modifier,
                        hint = when { p?.finished == true -> "İzlendi"; p != null && p.fraction > 0.01f -> "%${(p.fraction * 100).toInt()} izlendi"; else -> null },
                    )
                }
            }
        }
        SidePanel(panel == Panel.Sleep, "Uyku zamanlayıcısı") {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
            val opts = listOf(15, 30, 45, 60, 90)
            OptionRow("Kapalı", sleep == null, { sleep = null; panel = Panel.None }, Modifier.focusRequester(first))
            opts.forEach { m -> OptionRow("$m dakika", (sleep as? Sleep.At)?.label == "$m", { sleep = Sleep.At(System.currentTimeMillis() + m * 60_000L, "$m"); panel = Panel.None }) }
            OptionRow(if (req.kind == "episode") "Bu bölüm bitince" else "Film bitince", sleep == Sleep.EpisodeEnd, { sleep = Sleep.EpisodeEnd; panel = Panel.None })
        }

        // Kaldığın yerden devam et
        if (askResume) ResumePrompt(req, meta, onResume = { askResume = false; player.seekTo(req.startMs); player.play(); runCatching { rootFocus.requestFocus() } },
            onRestart = { askResume = false; player.seekTo(0); player.play(); runCatching { rootFocus.requestFocus() } })

        // Hata
        error?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color(0xE6050508)), contentAlignment = Alignment.Center) {
                val f = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { f.requestFocus() } }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
                    Wordmark(40)
                    Spacer(Modifier.height(22.dp))
                    Text("Bu video açılamadı", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = C.muted)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillBtn("Tekrar dene", { error = null; loading = true; player.prepare(); player.play() }, Modifier.focusRequester(f), primary = true)
                        PillBtn("Kapat", onClose)
                    }
                }
            }
        }

        // Uyku ekranı
        if (sleeping) Box(Modifier.fillMaxSize().background(Color(0xF2000000)), contentAlignment = Alignment.Center) {
            val f = remember { FocusRequester() }
            LaunchedEffect(Unit) { delay(80); runCatching { f.requestFocus() } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("İyi uykular", style = Display.copy(fontSize = 44.sp))
                Spacer(Modifier.height(10.dp))
                Text("Kaldığın yer kaydedildi.", color = C.muted)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillBtn("Devam et", { sleeping = false; player.play(); runCatching { rootFocus.requestFocus() } }, Modifier.focusRequester(f), primary = true)
                    PillBtn("Kapat", onClose)
                }
            }
        }
    }
}

@Composable
fun Spinner(size: androidx.compose.ui.unit.Dp = 48.dp) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "sp")
    val r by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "r")
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        drawArc(Color(0x26FFFFFF), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
        drawArc(Color(0xD9FFFFFF), r, 90f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Sonraki bölüm kartı: sağ altta görsel + ad + geri sayım */
@Composable
private fun NextEpisodeCard(next: Episode, req: PlayRequest, left: Int, focus: FocusRequester, onPlay: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomEnd) {
        Column(Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF012121C)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(C.fill2)) {
                    AsyncImage(model = next.image ?: req.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sonraki bölüm · $left", style = MaterialTheme.typography.labelMedium, color = C.muted)
                    Text("${next.season}. Sezon · ${next.num}. Bölüm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val n = episodeName(next.title, req.series?.name)
                    if (n.isNotBlank()) Text(n, style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillBtn("Oynat", onPlay, Modifier.focusRequester(focus), icon = Icons.Default.PlayArrow, primary = true)
                PillBtn("Jeneriği izle", onDismiss)
            }
        }
    }
}
