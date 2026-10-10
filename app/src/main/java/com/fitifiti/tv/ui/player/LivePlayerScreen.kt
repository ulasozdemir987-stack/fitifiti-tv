package com.fitifiti.tv.ui.player

import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.domain.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import com.fitifiti.tv.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.fitifiti.tv.data.catalog.now
import com.fitifiti.tv.data.catalog.fraction
import com.fitifiti.tv.data.catalog.next

@Composable
fun LivePlayerScreen(startChannelId: Int, onClose: () -> Unit) {
    val app = App.instance
    val ctx = LocalContext.current
    
    val allChannels by LiveManager.getVisibleChannels().collectAsStateWithLifecycle()
    val lists by LiveManager.getVisibleLists().collectAsStateWithLifecycle()
    
    var currentListName by remember { mutableStateOf("Tüm kanallar") }
    var activeListChannels by remember { mutableStateOf(emptyList<LiveChannel>()) }
    var channelIndex by remember { mutableIntStateOf(0) }
    var previousChannelId by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf(false) }
    var epgGrid by remember { mutableStateOf(false) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf("") }
    
    // önizlemede zaten oynayan kanal → aynı oynatıcı devralınır (anında açılır)
    val player = remember { com.fitifiti.tv.ui.screens.LiveHandoff.take(startChannelId) ?: buildPlayer(ctx, live = true) }
    var buffering by remember { mutableStateOf(player.playbackState != androidx.media3.common.Player.STATE_READY) }
    DisposableEffect(player) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == androidx.media3.common.Player.STATE_BUFFERING || state == androidx.media3.common.Player.STATE_IDLE }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                error = when (e.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Sağlayıcı yayını vermedi. Hesabın aynı anda tek bağlantıya izin veriyor olabilir; başka cihazda açıksa kapatıp tekrar dene."
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Bağlantı kurulamadı. İnternetini kontrol edip yeniden dene."
                    else -> "Yayın açılamadı (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})"
                }
            }
        }
        player.addListener(l); onDispose { player.removeListener(l) }
    }
    val scope = rememberCoroutineScope()
    val rootFocus = remember { FocusRequester() }

    // Init list and index
    LaunchedEffect(allChannels, currentListName) {
        if (allChannels.isEmpty()) return@LaunchedEffect
        activeListChannels = allChannels.filter { it.lists.contains(currentListName) }.ifEmpty { allChannels }
    }
    
    // Auto-select starting channel on first load
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(activeListChannels) {
        if (!initialized && activeListChannels.isNotEmpty()) {
            initialized = true
            val id = if (startChannelId <= 0) activeListChannels.first().channel.id else startChannelId
            val idx = activeListChannels.indexOfFirst { it.channel.id == id }
            channelIndex = if (idx >= 0) idx else 0
        }
    }

    val liveChannel = activeListChannels.getOrNull(channelIndex)
    val channel = liveChannel?.channel

    fun switchToId(id: Int) {
        val idx = activeListChannels.indexOfFirst { it.channel.id == id }
        if (idx >= 0) {
            previousChannelId = channel?.id
            channelIndex = idx
            banner = true; bannerTick++
        } else {
            // Find in all channels
            val global = allChannels.find { it.channel.id == id }
            if (global != null) {
                currentListName = "Tüm kanallar"
                previousChannelId = channel?.id
                channelIndex = allChannels.indexOf(global)
                banner = true; bannerTick++
            }
        }
    }
    
    fun switchToRelative(offset: Int) {
        if (activeListChannels.isEmpty()) return
        val newIdx = ((channelIndex + offset) % activeListChannels.size + activeListChannels.size) % activeListChannels.size
        previousChannelId = channel?.id
        channelIndex = newIdx
        banner = true; bannerTick++
    }

    // Number input processing
    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1500)
            val num = digits.toIntOrNull()
            if (num != null) {
                val target = activeListChannels.find { it.num == num } ?: allChannels.find { it.num == num }
                if (target != null) switchToId(target.channel.id)
            }
            digits = ""
        }
    }

    // Hide banner auto
    LaunchedEffect(bannerTick) {
        if (bannerTick > 0) {
            banner = true
            delay(5000)
            banner = false
        }
    }
    
    // Playback
    LaunchedEffect(channel?.id) {
        if (channel == null) return@LaunchedEffect
        error = null
        try {
            app.user.touchChannel(channel.id)
            val url = app.client().liveUrl(channel.id, true)
            val same = player.currentMediaItem?.localConfiguration?.uri?.toString() == url && player.playbackState != androidx.media3.common.Player.STATE_IDLE
            if (!same) {
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                player.prepare()
            }
            player.play()
        } catch (e: Exception) {
            error = e.message ?: "Oynatma hatası"
        }
    }

    LaunchedEffect(panel) { if (!panel) runCatching { rootFocus.requestFocus() } }
    BackHandler { if (epgGrid) epgGrid = false else if (panel) panel = false else if (banner) banner = false else onClose() }

    val epg = if (channel != null) rememberEpg(channel) else emptyList()
    val now = epg.now()
    val next = epg.next()

    DisposableEffect(Unit) {
        com.fitifiti.tv.data.remote.RemoteBus.screen.value = "player"
        onDispose {
            player.release()
            com.fitifiti.tv.data.remote.RemoteBus.player.value = null
            com.fitifiti.tv.data.remote.RemoteBus.screen.value = "app"
        }
    }
    LaunchedEffect(channel?.id, now?.title) {
        if (channel != null) com.fitifiti.tv.data.remote.RemoteBus.player.value = com.fitifiti.tv.data.remote.RemotePlayer(cleanChannelName(channel.name), now?.let { programmeHeadline(it.title) }, channel.icon, 0, 0, true)
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || panel) return@onPreviewKeyEvent false
                when (val code = e.key.nativeKeyCode) {
                    AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_CHANNEL_UP, AKey.KEYCODE_PAGE_UP -> { switchToRelative(-1); true }
                    AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_CHANNEL_DOWN, AKey.KEYCODE_PAGE_DOWN -> { switchToRelative(1); true }
                    AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_MENU -> {
                        if (digits.isNotEmpty()) {
                            val num = digits.toIntOrNull()
                            if (num != null) {
                                val target = activeListChannels.find { it.num == num } ?: allChannels.find { it.num == num }
                                if (target != null) switchToId(target.channel.id)
                            }
                            digits = ""
                        } else if (error == null || code != AKey.KEYCODE_DPAD_CENTER) {
                            panel = true
                        }
                        true
                    }
                    AKey.KEYCODE_INFO, AKey.KEYCODE_DPAD_RIGHT -> { bannerTick++; true }
                    AKey.KEYCODE_LAST_CHANNEL -> { previousChannelId?.let { switchToId(it) }; true }
                    in AKey.KEYCODE_0..AKey.KEYCODE_9 -> { digits = (digits + (code - AKey.KEYCODE_0)).take(4); true }
                    AKey.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) player.pause() else player.play(); true }
                    AKey.KEYCODE_GUIDE, AKey.KEYCODE_PROG_RED -> { epgGrid = true; true }
                    else -> false
                }
            },
    ) {
        VideoSurface(player, Modifier.fillMaxSize(), app.settings.value.subtitleScale)

        // Yükleniyor: ortada dönen halka (kanal değişiminde de)
        if (channel != null && error == null && buffering) {
            Box(Modifier.align(Alignment.Center)) { Spinner(44.dp) }
        }

                // Digits
        if (digits.isNotEmpty()) {
            val num = digits.toIntOrNull()
            val target = if (num != null) activeListChannels.find { it.num == num } ?: allChannels.find { it.num == num } else null
            Row(Modifier.align(Alignment.TopEnd).padding(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(digits, style = Display.copy(fontSize = 64.sp))
                if (target != null) {
                    Spacer(Modifier.width(16.dp))
                    Text(target.name, style = MaterialTheme.typography.headlineMedium, color = C.primary)
                }
            }
        }

        // Banner
        AnimatedVisibility(banner && !panel && channel != null && liveChannel != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(start = 56.dp, end = 56.dp, top = 80.dp, bottom = 40.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(liveChannel!!.num.toString(), style = Display.copy(fontSize = 42.sp, color = Color(0x99FFFFFF)), modifier = Modifier.width(90.dp))
                    Box(Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(10.dp)).background(C.fill2)) { ChannelLogo(channel!!, Modifier.fillMaxSize().padding(10.dp)) }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(liveChannel!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (now != null) {
                            Text(programmeHeadline(now.title), style = MaterialTheme.typography.bodyLarge, color = Color(0xE6FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(hhmm(now.start), style = MaterialTheme.typography.bodySmall, color = C.muted)
                                Box(Modifier.padding(horizontal = 10.dp).width(260.dp)) { ProgressLine(now.fraction(), Modifier.fillMaxWidth(), height = 3.dp, track = C.fill3) }
                                Text(hhmm(now.end), style = MaterialTheme.typography.bodySmall, color = C.muted)
                            }
                        }
                        if (next != null) Text("Sonra — ${hhmm(next.start)} ${programmeHeadline(next.title)}", style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // Panel
        AnimatedVisibility(panel, enter = fadeIn() + slideInHorizontally { -it / 3 }, exit = fadeOut() + slideOutHorizontally { -it / 3 }) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF0000000), 0.5f to Color(0x99000000), 1f to Color.Transparent))) {
                val state = rememberLazyListState()
                val cur = remember { FocusRequester() }
                LaunchedEffect(Unit) { state.scrollToItem((channelIndex - 3).coerceAtLeast(0)); delay(80); runCatching { cur.requestFocus() } }
                
                Column(Modifier.fillMaxHeight().width(500.dp).background(C.panel.copy(alpha = 0.95f)).padding(top = 28.dp, bottom = 28.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("←", color = C.muted, fontSize = 24.sp)
                        Text(currentListName, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = C.primary)
                        Text("→", color = C.muted, fontSize = 24.sp)
                    }
                    LazyColumn(state = state, contentPadding = PaddingValues(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(activeListChannels, key = { _, c -> c.channel.id }) { i, c ->
                            ChannelListRow(c, i == channelIndex, if (i == channelIndex) Modifier.focusRequester(cur) else Modifier,
                                onClick = { switchToId(c.channel.id); panel = false },
                                onLeftRight = { dir ->
                                    val idx = lists.indexOf(currentListName)
                                    if (idx >= 0) {
                                        val nextIdx = ((idx + dir) % lists.size + lists.size) % lists.size
                                        currentListName = lists[nextIdx]
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(epgGrid, enter = fadeIn(), exit = fadeOut()) {
            EpgGrid(allChannels, channel?.id) { id -> 
                switchToId(id)
                epgGrid = false 
            }
        }

        error?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color(0xD9050508)), contentAlignment = Alignment.Center) {
                val f = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { f.requestFocus() } }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
                    Text(liveChannel?.name ?: "", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = C.muted)
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillBtn("Yeniden bağlan", { error = null; player.prepare(); player.play() }, Modifier.focusRequester(f), primary = true)
                        PillBtn("Kanal listesi", { error = null; panel = true })
                        PillBtn("Kapat", onClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelListRow(c: LiveChannel, current: Boolean, modifier: Modifier, onClick: () -> Unit, onLeftRight: (Int) -> Unit) {
    val epg = rememberEpg(c.channel)
    val now = epg.now()
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown) {
                if (it.key.nativeKeyCode == AKey.KEYCODE_DPAD_LEFT) { onLeftRight(-1); true }
                else if (it.key.nativeKeyCode == AKey.KEYCODE_DPAD_RIGHT) { onLeftRight(1); true }
                else false
            } else false
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (current) C.fill3 else Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.num.toString(), fontSize = 14.sp, modifier = Modifier.width(44.dp), color = LocalContentColor.current.copy(alpha = 0.6f))
            Box(Modifier.size(64.dp, 38.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1AFFFFFF))) { ChannelLogo(c.channel, Modifier.fillMaxSize().padding(4.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) {
                    Text(programmeHeadline(now.title), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    ProgressLine(now.fraction(), Modifier.fillMaxWidth(0.7f), height = 2.dp, track = LocalContentColor.current.copy(alpha = 0.15f))
                }
            }
        }
    }
}
