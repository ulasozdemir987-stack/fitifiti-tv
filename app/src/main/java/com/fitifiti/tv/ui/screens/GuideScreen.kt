package com.fitifiti.tv.ui.screens

import android.view.KeyEvent as AKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.data.catalog.fraction
import com.fitifiti.tv.data.local.ReminderEntity
import com.fitifiti.tv.data.xtream.EpgItem
import com.fitifiti.tv.domain.LiveChannel
import com.fitifiti.tv.domain.LiveManager
import com.fitifiti.tv.domain.programmeHeadline
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.LocalScreenActive
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.player.VideoSurface
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HALF_HOUR = 30 * 60_000L
private const val WINDOW = 3 * 3600_000L

/**
 * Yayın akışı — OwnTV'deki TV rehberi: üstte odaktaki programın kanalı (canlı önizleme) ve bilgisi, altında kanal × saat
 * çizelgesi, mor → turkuaz "şimdi" çizgisi. ←/→ programlar arasında (pencerenin kenarında saat 1 saat kayar), ↑/↓ kanallar.
 * OK: yayındaki program → kanalı aç; ilerideki → hatırlatıcı kur.
 */
@Composable
fun GuideScreen() {
    val app = App.instance
    val actions = LocalActions.current
    val channels by LiveManager.getVisibleChannels().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var nowT by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowT = System.currentTimeMillis() } }
    var windowStart by remember { mutableLongStateOf(nowT - nowT % HALF_HOUR - HALF_HOUR) }
    val windowEnd = windowStart + WINDOW
    var sel by remember { mutableStateOf<Pair<LiveChannel, EpgItem?>?>(null) }
    val shownCh = sel?.first ?: channels.firstOrNull()
    val shownProg = sel?.second
    val preview = rememberPreviewPlayer()
    val active = LocalScreenActive.current
    val canPlay = remember { !android.os.Build.FINGERPRINT.contains("robolectric", true) }
    LaunchedEffect(shownCh?.channel?.id, active) {
        val c = shownCh ?: return@LaunchedEffect
        if (!active || !canPlay) return@LaunchedEffect
        delay(1200); runCatching { preview.play(c.channel.id, app.client().liveUrl(c.channel.id, true)) }
    }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { delay(2500); toast = null } }

    fun open(c: LiveChannel, p: EpgItem?) {
        val t = System.currentTimeMillis()
        if (p == null || p.start <= t) { preview.stopNow(); actions.playChannel(c.channel.id, channels.map { it.channel.id }) }
        else scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            app.db.reminders().upsert(ReminderEntity("${c.channel.id}-${p.start}", app.user.profileId.value, programmeHeadline(p.title), c.channel.id, p.start, p.end))
            toast = "Hatırlatıcı kuruldu · ${hhmm(p.start)} ${programmeHeadline(p.title)}"
        }
    }

    Column(Modifier.fillMaxSize().background(C.bg).background(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.16f), Color.Transparent), center = androidx.compose.ui.geometry.Offset.Zero, radius = 900f))
        .padding(start = 48.dp, end = 40.dp, top = 20.dp)) {
        // üst: önizleme + bilgi
        Row(Modifier.fillMaxWidth().height(178.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                val p = preview.player
                if (p != null && preview.playingId == shownCh?.channel?.id) VideoSurface(p, Modifier.fillMaxSize())
                else if (shownCh != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChannelLogo(shownCh.channel, Modifier.fillMaxWidth(0.32f).aspectRatio(1.6f)) }
                if (shownCh != null) Text("${shownCh.num} · ${shownCh.name}", Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(10.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f).padding(top = 34.dp)) {
                if (shownProg != null) {
                    val live = shownProg.start <= nowT && shownProg.end > nowT
                    Text(listOfNotNull(if (live) "Şu an yayında" else null, "${hhmm(shownProg.start)} – ${hhmm(shownProg.end)}", "${(shownProg.end - shownProg.start) / 60_000} dk", shownCh?.name).joinToString(" · "),
                        color = C.teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(programmeHeadline(shownProg.title), style = Display.copy(fontSize = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shownProg.description.isNotBlank()) Text(shownProg.description, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xCCFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                } else Text("Yayın akışı", style = Display.copy(fontSize = 26.sp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.align(Alignment.End)) { KeyHint("OK", "İzle / Hatırlat"); KeyHint("▲▼", "Kanallar"); KeyHint("◀▶", "Saat") }
            }
        }
        Spacer(Modifier.height(14.dp))
        // saat başlığı
        var gridW by remember { mutableIntStateOf(1) }
        val density = LocalDensity.current
        val chCol = 200.dp
        Box(Modifier.fillMaxWidth().height(26.dp).onSizeChanged { gridW = it.width }) {
            val tlW = with(density) { (gridW - chCol.roundToPx()).toDp() }
            Text("Yayın akışı", style = Display.copy(fontSize = 18.sp), modifier = Modifier.align(Alignment.CenterStart))
            for (i in 0 until 6) {
                val t = windowStart + i * HALF_HOUR
                Text(hhmm(t), fontSize = 12.sp, color = C.muted, modifier = Modifier.offset(x = chCol + tlW * (i.toFloat() / 6f)).align(Alignment.CenterStart))
            }
            if (nowT in windowStart until windowEnd) {
                Text(hhmm(nowT), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black,
                    modifier = Modifier.offset(x = chCol + tlW * ((nowT - windowStart).toFloat() / WINDOW) - 18.dp).align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(C.primary, C.teal))).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            val tlW = with(density) { (gridW - chCol.roundToPx()).toDp() }
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(channels, key = { it.channel.id }) { c ->
                    GuideRow(c, windowStart, windowEnd, nowT, chCol, tlW,
                        onFocus = { p -> sel = c to p },
                        onOpen = { p -> open(c, p) },
                        onShift = { dir ->
                            val ns = windowStart + dir * 3600_000L
                            val min = nowT - nowT % HALF_HOUR - 6 * 3600_000L
                            val max = nowT + 44 * 3600_000L
                            if (ns in min..max) { windowStart = ns; true } else false
                        })
                }
            }
            // şimdi çizgisi
            if (nowT in windowStart until windowEnd) Box(Modifier.offset(x = chCol + tlW * ((nowT - windowStart).toFloat() / WINDOW)).width(2.dp).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(C.primary, C.teal.copy(alpha = 0.4f)))))
        }
    }
    toast?.let { t ->
        Box(Modifier.fillMaxSize().padding(bottom = 30.dp), contentAlignment = Alignment.BottomCenter) {
            Text(t, color = Color.White, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.panel).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun GuideRow(c: LiveChannel, from: Long, to: Long, nowT: Long, chCol: Dp, tlW: Dp, onFocus: (EpgItem?) -> Unit, onOpen: (EpgItem?) -> Unit, onShift: (Int) -> Boolean) {
    val progs by produceState(emptyList<EpgItem>(), c.channel.id, from) {
        val id = c.channel.epgId
        val r = if (id != null) runCatching { EpgCache.range(listOf(id), from, to)[id] }.getOrNull().orEmpty() else emptyList()
        value = r.ifEmpty { NowNextMemo.get(c.channel) }.filter { it.end > from && it.start < to }.sortedBy { it.start }
    }
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(chCol).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${c.num}", fontSize = 13.sp, color = C.muted, modifier = Modifier.width(34.dp))
            Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1FFFFFFF))) { ChannelLogo(c.channel, Modifier.fillMaxSize().padding(3.dp)) }
            Spacer(Modifier.width(8.dp))
            Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(tlW).fillMaxHeight()) {
            if (progs.isEmpty()) GuideBlock(null, "Program bilgisi yok", 0.dp, tlW, false, false, { onFocus(null) }, { onOpen(null) }, first = true, last = true, onShift = onShift)
            progs.forEachIndexed { i, p ->
                val s = maxOf(p.start, from); val e = minOf(p.end, to)
                val x = tlW * ((s - from).toFloat() / (to - from)); val w = tlW * ((e - s).toFloat() / (to - from))
                GuideBlock(p, programmeHeadline(p.title), x, w, p.start <= nowT && p.end > nowT, p.end <= nowT, { onFocus(p) }, { onOpen(p) },
                    first = i == 0, last = i == progs.lastIndex, onShift = onShift, nowT = nowT)
            }
        }
    }
}

@Composable
private fun GuideBlock(p: EpgItem?, title: String, x: Dp, w: Dp, live: Boolean, past: Boolean, onFocus: () -> Unit, onOpen: () -> Unit,
                       first: Boolean, last: Boolean, onShift: (Int) -> Boolean, nowT: Long = 0) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.offset(x = x).width((w - 4.dp).coerceAtLeast(6.dp)).fillMaxHeight()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key.nativeKeyCode) {
                    AKey.KEYCODE_DPAD_LEFT -> if (first) onShift(-1) else false
                    AKey.KEYCODE_DPAD_RIGHT -> if (last) onShift(1) else false
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (live) Color(0x24FFFFFF) else Color(0x12FFFFFF), focusedContainerColor = C.primary.copy(alpha = 0.22f),
            contentColor = if (past) C.muted else Color.White, focusedContentColor = Color.White),
        border = FocusRing,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 13.sp, fontWeight = if (live) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p != null) Text("${hhmm(p.start)} – ${hhmm(p.end)}", fontSize = 10.sp, color = LocalContentColor.current.copy(alpha = 0.6f), maxLines = 1)
        }
        if (live && p != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { ProgressLine(p.fraction(nowT), Modifier.fillMaxWidth(), height = 2.dp, track = Color.Transparent) }
    }
}
