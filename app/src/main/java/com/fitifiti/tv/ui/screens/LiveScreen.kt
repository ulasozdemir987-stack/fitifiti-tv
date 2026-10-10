package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.data.catalog.fraction
import com.fitifiti.tv.data.catalog.next
import com.fitifiti.tv.data.catalog.now
import com.fitifiti.tv.data.xtream.Channel
import com.fitifiti.tv.data.xtream.EpgItem
import com.fitifiti.tv.domain.LiveChannel
import com.fitifiti.tv.domain.LiveManager
import com.fitifiti.tv.domain.categoryLabel
import com.fitifiti.tv.domain.programmeHeadline
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.rememberFocus
import com.fitifiti.tv.ui.LocalScreenActive
import com.fitifiti.tv.ui.Route
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.player.VideoSurface
import com.fitifiti.tv.ui.player.buildPlayer
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay

/** Satırlar için şimdi/sonra önbelleği (kaydırınca her satır yeniden sormasın; 2 dk) */
object NowNextMemo {
    private val map = java.util.concurrent.ConcurrentHashMap<Int, Pair<Long, List<EpgItem>>>()
    fun peek(id: Int): List<EpgItem>? = map[id]?.takeIf { System.currentTimeMillis() - it.first < 120_000 }?.second
    suspend fun get(c: Channel): List<EpgItem> =
        peek(c.id) ?: runCatching { EpgCache.getNowNext(c.epgId, c.id) }.getOrDefault(emptyList()).also { map[c.id] = System.currentTimeMillis() to it }
    /** Önizlemeler için doldurma */
    fun seed(id: Int, list: List<EpgItem>) { map[id] = System.currentTimeMillis() to list }
}

@Composable
fun rememberNowNext(c: Channel): List<EpgItem> {
    val v by produceState(NowNextMemo.peek(c.id) ?: emptyList(), c.id) { value = NowNextMemo.get(c) }
    return v
}

/** Kanal süzgeci: tüm kanallar / kullanıcının listesi / sağlayıcı kategorisi */
private data class LiveFilter(val kind: String, val id: String?, val label: String)

private val LiveFilterSaver = androidx.compose.runtime.saveable.Saver<LiveFilter, List<String?>>({ listOf(it.kind, it.id, it.label) }, { LiveFilter(it[0]!!, it[1], it[2]!!) })

/**
 * Canlı TV — OwnTV düzeni: solda numaralı kanal listesi (logo, ad, şimdiki program, kalan süre, ilerleme), sağda
 * odaktaki kanalın canlı önizlemesi + program bilgisi + sıradaki. Önizleme kanalda ~1 sn durunca başlar; hesap tek
 * bağlantılı olabileceğinden kanal açılırken / ekran arkada kalınca hemen durdurulur.
 */
@Composable
fun LiveScreen() {
    val app = App.instance
    val actions = LocalActions.current
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val all by LiveManager.getVisibleChannels().collectAsStateWithLifecycle()
    val lists by LiveManager.getVisibleLists().collectAsStateWithLifecycle()
    val recent by app.user.recentChannels.collectAsStateWithLifecycle()
    var filter by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = LiveFilterSaver) { mutableStateOf(LiveFilter("all", null, "Tüm kanallar")) }
    val channels = remember(all, filter) {
        when (filter.kind) {
            "list" -> all.filter { filter.label in it.lists }
            "cat" -> all.filter { it.channel.categoryId == filter.id }
            else -> all
        }.distinctBy { it.channel.id }
    }
    val focused = remember { mutableStateOf<LiveChannel?>(null) }
    var dialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<LiveChannel?>(null) }
    val listState = rememberLazyListState()
    val startIndex = remember(channels, recent.firstOrNull()?.channelId) {
        recent.firstOrNull()?.let { r -> channels.indexOfFirst { it.channel.id == r.channelId } }?.takeIf { it >= 0 } ?: 0
    }
    LaunchedEffect(channels.size, filter) { if (channels.isNotEmpty()) runCatching { listState.scrollToItem((startIndex - 3).coerceAtLeast(0)) } }

    // uydu alıcısı gibi: rakamlarla kanal numarası yaz → 1,3 sn sonra o kanala (yoksa sonraki numaraya) atlar
    var digits by remember { mutableStateOf("") }
    var jumpId by remember { mutableStateOf<Int?>(null) }
    val digitTarget = remember(digits, channels) { digits.toIntOrNull()?.let { n -> channels.firstOrNull { it.num == n } ?: channels.firstOrNull { it.num > n } } }
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1600)
        digitTarget?.let { t -> val i = channels.indexOf(t); runCatching { listState.scrollToItem((i - 3).coerceAtLeast(0)) }; jumpId = t.channel.id }
        digits = ""
    }
    val preview = rememberPreviewPlayer()
    fun watch(c: LiveChannel) { preview.handOff(c.channel.id); actions.playChannel(c.channel.id, channels.map { it.channel.id }) }

    Box(Modifier.fillMaxSize().cornerGlow()
        .onPreviewKeyEvent { e ->
            val code = e.key.nativeKeyCode
            if (code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) {
                if (e.type == KeyEventType.KeyDown) digits = (digits + (code - android.view.KeyEvent.KEYCODE_0)).take(5)
                true
            } else false
        }) {
        Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 40.dp, top = 22.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            // sol: başlık + araç çubuğu + kanal listesi
            Column(Modifier.weight(0.52f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Canlı TV", style = Display.copy(fontSize = 28.sp))
                    Spacer(Modifier.width(14.dp))
                    Text(filter.label, color = C.teal, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp).weight(1f, fill = false))
                    Text(" · ${channels.size} kanal", color = C.muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveChip(Icons.AutoMirrored.Filled.List, "Kategori") { dialog = true }
                    LiveChip(Icons.Default.EmojiEvents, "Takvim") { preview.stopNow(); actions.nav.push(Route.LiveCalendar) }
                    LiveChip(Icons.Default.Edit, "Kanal düzenle") { preview.stopNow(); actions.nav.push(Route.ChannelEdit) }
                }
                Spacer(Modifier.height(10.dp))
                if (channels.isEmpty()) Text(if (cat.channels.isEmpty()) "Bu hesapta canlı kanal yok" else "Bu listede kanal yok", color = C.muted, modifier = Modifier.padding(top = 20.dp))
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.channel.id }) { _, c ->
                        ChannelRow(c, onClick = { watch(c) }, onLong = { menu = c }, onFocus = { focused.value = c },
                            jump = c.channel.id == jumpId, onJumped = { jumpId = null })
                    }
                }
            }
            // sağ: önizleme + program
            Column(Modifier.weight(0.48f).fillMaxHeight().padding(top = 64.dp)) {
                LiveSide(channels, focused, preview)
                Spacer(Modifier.weight(1f))
                Row(Modifier.align(Alignment.End)) { KeyHint("OK", "İzle"); KeyHint("Basılı OK", "Seçenekler"); KeyHint("◀", "Menü") }
            }
        }
    }

    if (digits.isNotEmpty()) Box(Modifier.fillMaxSize().padding(top = 18.dp, end = 40.dp), contentAlignment = Alignment.TopEnd) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xF00E0E17)).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(digits, style = Display.copy(fontSize = 54.sp, brush = Brush.horizontalGradient(listOf(C.primary, C.teal))))
            Text(digitTarget?.let { "${it.num} · ${it.name}" } ?: "Kanal yok", fontSize = 14.sp, color = if (digitTarget != null) Color.White else C.muted, maxLines = 1)
        }
    }
    if (dialog) {
        val opts = buildList {
            add("Tüm kanallar" to { filter = LiveFilter("all", null, "Tüm kanallar") })
            lists.filter { it != "Tüm kanallar" }.forEach { l -> add("Listem: $l" to { filter = LiveFilter("list", null, l) }) }
            cat.sortedCats(cat.liveCats).forEach { c -> add(categoryLabel(c.name) to { filter = LiveFilter("cat", c.id, categoryLabel(c.name)) }) }
        }
        OptionsDialog("Kanallar", opts) { dialog = false }
    }
    menu?.let { c ->
        OptionsDialog("${c.num} · ${c.name}", listOf(
            "İzle" to { watch(c) },
            "Kanal düzenle" to { preview.stopNow(); actions.nav.push(Route.ChannelEdit) },
        )) { menu = null }
    }
}

/** Sağ taraf (önizleme + program): odak değişince yalnız burası yeniden çizilir */
@Composable
private fun LiveSide(channels: List<LiveChannel>, focused: State<LiveChannel?>, preview: PreviewPlayer) {
    val f = focused.value
    val shown = f?.takeIf { x -> channels.any { it.channel.id == x.channel.id } } ?: channels.firstOrNull()
    LivePreviewBox(shown, preview)
    Spacer(Modifier.height(14.dp))
    if (shown != null) ProgrammeInfo(shown)
}

@Composable
private fun LiveChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x14FFFFFF), focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Kanal satırı: numara · logo · ad / şimdiki program · kalan süre / ilerleme */
@Composable
fun ChannelRow(c: LiveChannel, onClick: () -> Unit, onLong: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier, jump: Boolean = false, onJumped: () -> Unit = {}) {
    val epg = rememberNowNext(c.channel)
    val fr = remember { FocusRequester() }
    LaunchedEffect(jump) { if (jump) { withFrameNanos {}; runCatching { fr.requestFocus() }; onJumped() } }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) { if (focused) { delay(120); onFocus() } }
    val now = epg.now()
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(58.dp).focusRequester(fr).rememberFocus().onFocusChanged { focused = it.isFocused }.okClicks(onClick, onLong),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = C.primary.copy(alpha = 0.16f), contentColor = Color.White, focusedContentColor = Color.White),
        border = FocusRing,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${c.num}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (focused) Color.White else C.muted, modifier = Modifier.width(42.dp))
            Box(Modifier.size(54.dp, 36.dp).clip(RoundedCornerShape(7.dp)).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.Center) {
                ChannelLogo(c.channel, Modifier.fillMaxSize().padding(4.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(programmeHeadline(now.title), fontSize = 12.sp, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(" · ${minutesLeft(now)}", fontSize = 12.sp, color = C.muted, maxLines = 1)
                    }
                    Spacer(Modifier.height(3.dp))
                    ProgressLine(now.fraction(), Modifier.fillMaxWidth(0.62f), height = 2.dp, track = Color(0x24FFFFFF))
                }
            }
        }
    }
}

fun minutesLeft(e: EpgItem): String {
    val m = ((e.end - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60} sa ${m % 60} dk kaldı" else "$m dk kaldı"
}

/** Önizlemeden tam ekrana devredilen oynatıcı (bir kez alınır) */
object LiveHandoff {
    private var held: Pair<ExoPlayer, Int>? = null
    fun put(p: ExoPlayer, id: Int) { held?.first?.release(); held = p to id }
    fun take(id: Int): ExoPlayer? = held?.takeIf { it.second == id }?.first.also { held = null } ?: run { held?.first?.release(); held = null; null }
}

/** Önizleme oynatıcısı: tek örnek, ekran arkada kalınca / sekmeden çıkınca durur ve bırakılır */
class PreviewPlayer(private val make: () -> ExoPlayer) {
    var player by mutableStateOf<ExoPlayer?>(null); private set
    var playingId by mutableStateOf<Int?>(null); private set
    fun play(id: Int, url: String) {
        val p = player ?: make().also { player = it }
        if (playingId == id) return
        playingId = id
        p.setMediaItem(androidx.media3.common.MediaItem.fromUri(url)); p.prepare(); p.playWhenReady = true
    }
    fun stopNow() { player?.stop(); player?.clearMediaItems(); playingId = null }
    /**
     * Tam ekrana geçiş: aynı kanal zaten oynuyorsa oynatıcı kapatılmadan tam ekran oynatıcıya devredilir → kanal
     * anında açılır (yeniden bağlanma / tamponlama yok, tek bağlantı korunur). Değilse durdurulur.
     */
    fun handOff(id: Int) {
        val p = player
        if (p != null && playingId == id && p.playbackState != androidx.media3.common.Player.STATE_IDLE) {
            LiveHandoff.put(p, id); player = null; playingId = null
        } else stopNow()
    }
    fun release() { player?.release(); player = null; playingId = null }
}

@Composable
fun rememberPreviewPlayer(): PreviewPlayer {
    val ctx = LocalContext.current
    val pp = remember { PreviewPlayer { buildPlayer(ctx, live = true) } }
    val active = LocalScreenActive.current
    LaunchedEffect(active) { if (!active) pp.stopNow() }
    DisposableEffect(Unit) { onDispose { pp.release() } }
    return pp
}

/** Robolectric önizlemelerinde oynatıcı kurulmaz */
private val canPreview = !android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

@Composable
private fun LivePreviewBox(c: LiveChannel?, pp: PreviewPlayer) {
    val app = App.instance
    val active = LocalScreenActive.current
    LaunchedEffect(c?.channel?.id, active) {
        if (c == null || !active || !canPreview) return@LaunchedEffect
        delay(1100)
        runCatching { pp.play(c.channel.id, app.client().liveUrl(c.channel.id, true)) }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
        val p = pp.player
        if (p != null && pp.playingId == c?.channel?.id) VideoSurface(p, Modifier.fillMaxSize())
        else if (c != null) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.18f), Color.Transparent))), contentAlignment = Alignment.Center) {
            ChannelLogo(c.channel, Modifier.fillMaxWidth(0.3f).aspectRatio(1.6f))
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFEF4444))); Spacer(Modifier.width(6.dp))
            Text("CANLI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        if (c != null) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))) { ChannelLogo(c.channel, Modifier.fillMaxSize().padding(3.dp)) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${c.num}", fontSize = 11.sp, color = C.muted)
                Text(c.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProgrammeInfo(c: LiveChannel) {
    val epg = rememberNowNext(c.channel)
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick = System.currentTimeMillis() } }
    val now = epg.now(tick)
    val next = epg.next(tick)
    if (now == null) { Text("Program bilgisi yok", color = C.muted); return }
    Text(programmeHeadline(now.title), style = Display.copy(fontSize = 24.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text("${hhmm(now.start)} – ${hhmm(now.end)}", color = C.muted, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressLine(now.fraction(tick), Modifier.weight(1f), height = 3.dp, track = Color(0x24FFFFFF))
        Spacer(Modifier.width(10.dp))
        Text(minutesLeft(now), fontSize = 12.sp, color = C.muted)
    }
    if (now.description.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(now.description, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xCCFFFFFF), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    if (next != null) {
        Spacer(Modifier.height(10.dp))
        Row {
            Text(hhmm(next.start), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.teal)
            Spacer(Modifier.width(8.dp))
            Text(programmeHeadline(next.title), fontSize = 13.sp, color = Color(0xD9FFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
