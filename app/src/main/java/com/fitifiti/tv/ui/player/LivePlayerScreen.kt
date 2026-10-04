package com.fitifiti.tv.ui.player

import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.fraction
import com.fitifiti.tv.data.catalog.next
import com.fitifiti.tv.data.catalog.now
import com.fitifiti.tv.domain.cleanChannelName
import com.fitifiti.tv.domain.programmeHeadline
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay

/**
 * Canlı oynatıcı: ↑/↓ kanal, rakamlar kanal numarası, OK kanal listesi, son kanal tuşu önceki kanal.
 * Hesaplar çoğunlukla tek bağlantılı → kanal değişiminde aynı oynatıcıda kaynak değişir (eski bağlantı kapanır).
 */
@Composable
fun LivePlayerScreen(channelId: Int, list: List<Int>, onClose: () -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val channels = remember(list, cat) { list.mapNotNull { cat.channelById[it] }.ifEmpty { listOfNotNull(cat.channelById[channelId]) } }
    var index by remember { mutableIntStateOf(channels.indexOfFirst { it.id == channelId }.coerceAtLeast(0)) }
    var previous by remember { mutableIntStateOf(-1) }
    val channel = channels.getOrNull(index) ?: return
    val player = remember { buildPlayer(ctx, live = true) }
    var useTs by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") }
    val rootFocus = remember { FocusRequester() }

    fun switchTo(i: Int) {
        if (channels.isEmpty()) return
        val n = ((i % channels.size) + channels.size) % channels.size
        if (n != index) { previous = index; index = n; useTs = false }
        bannerTick++
    }

    // Kaynak: önce HLS (.m3u8), olmazsa düz TS
    LaunchedEffect(channel.id, useTs) {
        error = null; loading = true
        app.user.touchChannel(channel.id)
        player.setMediaItem(mediaItem(app.client().liveUrl(channel.id, hls = !useTs), live = true))
        player.prepare(); player.play()
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { loading = state == Player.STATE_BUFFERING; if (state == Player.STATE_READY) error = null }
            override fun onPlayerError(e: PlaybackException) {
                if (!useTs) { useTs = true; return }
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { player.seekToDefaultPosition(); player.prepare(); return }
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sağlayıcı yayını vermedi (bağlantı sınırı dolu ya da kanal kapalı olabilir)."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Yayın sunucusuna bağlanılamadı."
                    else -> "Yayın açılamadı."
                } + " (${e.errorCodeName})"
                loading = false
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.stop() else if (e == Lifecycle.Event.ON_START && player.playbackState == Player.STATE_IDLE) { player.prepare(); player.play() } }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(bannerTick) { banner = true; delay(4_500); banner = false }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1_500)
        val n = digits.toIntOrNull()
        val i = channels.indexOfFirst { it.num == n }.takeIf { it >= 0 } ?: n?.minus(1)?.takeIf { it in channels.indices }
        if (i != null) switchTo(i)
        digits = ""
    }
    LaunchedEffect(panel) { if (!panel) runCatching { rootFocus.requestFocus() } }

    BackHandler { if (panel) panel = false else if (banner) banner = false else onClose() }

    val epg = rememberEpg(channel)
    val now = epg.now(); val next = epg.next()
    DisposableEffect(Unit) {
        com.fitifiti.tv.data.remote.RemoteBus.screen.value = "player"
        onDispose { com.fitifiti.tv.data.remote.RemoteBus.player.value = null; com.fitifiti.tv.data.remote.RemoteBus.screen.value = "app" }
    }
    LaunchedEffect(channel.id, now?.title) {
        com.fitifiti.tv.data.remote.RemoteBus.player.value = com.fitifiti.tv.data.remote.RemotePlayer(cleanChannelName(channel.name), now?.let { programmeHeadline(it.title) }, channel.icon, 0, 0, true)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || panel) return@onPreviewKeyEvent false
                when (val code = e.key.nativeKeyCode) {
                    AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_CHANNEL_UP, AKey.KEYCODE_PAGE_UP -> { switchTo(index - 1); true }
                    AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_CHANNEL_DOWN, AKey.KEYCODE_PAGE_DOWN -> { switchTo(index + 1); true }
                    AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_MENU -> { if (error == null || code != AKey.KEYCODE_DPAD_CENTER) panel = true; error == null || code != AKey.KEYCODE_DPAD_CENTER }
                    AKey.KEYCODE_INFO, AKey.KEYCODE_DPAD_RIGHT -> { bannerTick++; true }
                    AKey.KEYCODE_LAST_CHANNEL -> { if (previous >= 0) switchTo(previous); true }
                    in AKey.KEYCODE_0..AKey.KEYCODE_9 -> { digits = (digits + (code - AKey.KEYCODE_0)).take(4); true }
                    AKey.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) player.pause() else player.play(); true }
                    else -> false
                }
            },
    ) {
        VideoSurface(player, Modifier.fillMaxSize(), app.settings.value.subtitleScale)
        if (loading && error == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { com.fitifiti.tv.ui.components.AnimatedBrandLogo(240.dp) }

        // Kanal numarası yazılıyor
        if (digits.isNotEmpty()) Text(digits, style = Display.copy(fontSize = 64.sp), modifier = Modifier.align(Alignment.TopEnd).padding(48.dp))

        // Bilgi şeridi
        AnimatedVisibility(banner && !panel, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.num.takeIf { it > 0 }?.toString() ?: "${index + 1}", style = Display.copy(fontSize = 34.sp, color = Color(0x99FFFFFF)), modifier = Modifier.width(90.dp))
                    Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(10.dp)).background(C.fill2)) { ChannelLogo(channel, Modifier.fillMaxSize().padding(10.dp)) }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cleanChannelName(channel.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (now != null) {
                            Text(programmeHeadline(now.title), style = MaterialTheme.typography.bodyLarge, color = Color(0xE6FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(hhmm(now.start), style = MaterialTheme.typography.bodySmall, color = C.muted)
                                Box(Modifier.padding(horizontal = 10.dp).width(260.dp)) { ProgressLine(now.fraction(), Modifier.fillMaxWidth(), height = 3.dp, track = C.fill3) }
                                Text(hhmm(now.end), style = MaterialTheme.typography.bodySmall, color = C.muted)
                            }
                        }
                        if (next != null) Text("Sonra · ${hhmm(next.start)} ${programmeHeadline(next.title)}", style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text("↑↓ kanal · OK liste", style = MaterialTheme.typography.labelSmall, color = C.faint)
                }
            }
        }

        // Kanal listesi
        AnimatedVisibility(panel, enter = fadeIn() + slideInHorizontally { -it / 3 }, exit = fadeOut() + slideOutHorizontally { -it / 3 }) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF0000000), 0.5f to Color(0x99000000), 1f to Color.Transparent))) {
                val state = rememberLazyListState()
                val cur = remember { FocusRequester() }
                LaunchedEffect(Unit) { state.scrollToItem((index - 3).coerceAtLeast(0)); delay(80); runCatching { cur.requestFocus() } }
                LazyColumn(state = state, modifier = Modifier.fillMaxHeight().width(500.dp).background(C.panel.copy(alpha = 0.95f)), contentPadding = PaddingValues(vertical = 28.dp, horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(channels, key = { i, c -> "$i-${c.id}" }) { i, c ->
                        ChannelListRow(c, i, i == index, if (i == index) Modifier.focusRequester(cur) else Modifier) { switchTo(i); panel = false }
                    }
                }
            }
        }

        error?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color(0xD9050508)), contentAlignment = Alignment.Center) {
                val f = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { f.requestFocus() } }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(cleanChannelName(channel.name), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = C.muted)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillBtn("Yeniden bağlan", { error = null; useTs = false; player.setMediaItem(mediaItem(app.client().liveUrl(channel.id, true), true)); player.prepare(); player.play() }, Modifier.focusRequester(f), primary = true)
                        PillBtn("Kanal listesi", { error = null; panel = true })
                        PillBtn("Kapat", onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelListRow(c: com.fitifiti.tv.data.xtream.Channel, i: Int, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val epg = rememberEpg(c)
    val now = epg.now()
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (current) C.fill3 else Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.num.takeIf { it > 0 }?.toString() ?: "${i + 1}", fontSize = 14.sp, modifier = Modifier.width(44.dp), color = LocalContentColor.current.copy(alpha = 0.6f))
            Box(Modifier.size(64.dp, 38.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1AFFFFFF))) { ChannelLogo(c, Modifier.fillMaxSize().padding(4.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cleanChannelName(c.name), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) {
                    Text(programmeHeadline(now.title), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    ProgressLine(now.fraction(), Modifier.fillMaxWidth(0.7f), height = 2.dp, track = LocalContentColor.current.copy(alpha = 0.15f))
                }
            }
        }
    }
}
