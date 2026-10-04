package com.fitifiti.tv.ui.components

import android.view.KeyEvent as AKey
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.data.xtream.EpgItem
import com.fitifiti.tv.domain.LiveChannel
import com.fitifiti.tv.domain.programmeHeadline
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EpgGrid(channels: List<LiveChannel>, currentChannelId: Int?, onChannelSelect: (Int) -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var offsetHours by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    
    val focusCur = remember { FocusRequester() }
    val listState = rememberLazyListState()
    
    val baseTime = currentTime + (offsetHours * 3600000L)
    
    LaunchedEffect(Unit) {
        val idx = channels.indexOfFirst { it.channel.id == currentChannelId }.coerceAtLeast(0)
        listState.scrollToItem(idx)
        delay(100)
        runCatching { focusCur.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Color(0xE6050508)).padding(top = 32.dp, bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Yayın Akışı", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.weight(1f))
            Text("←/→ Zamanı Değiştir", color = C.muted)
        }
        
        // Time header
        Row(Modifier.fillMaxWidth().padding(start = 132.dp)) {
            for (i in 0..4) {
                val cal = Calendar.getInstance().apply { 
                    timeInMillis = baseTime
                    set(Calendar.MINUTE, if (get(Calendar.MINUTE) < 30) 0 else 30)
                    set(Calendar.SECOND, 0)
                    add(Calendar.MINUTE, i * 30)
                }
                val clock = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
                Text(clock.format(cal.time), Modifier.width(180.dp), color = C.muted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(channels, key = { _, c -> c.channel.id }) { idx, c ->
                val isCurrent = c.channel.id == currentChannelId
                Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Channel Info
                    Column(Modifier.width(100.dp).padding(end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp, 38.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1AFFFFFF))) { ChannelLogo(c.channel, Modifier.fillMaxSize().padding(4.dp)) }
                        Text(c.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isCurrent) C.primary else Color.White)
                    }
                    
                    // EPG Row
                    val epg by produceState(emptyList<EpgItem>(), c.channel.id, offsetHours) {
                        value = EpgCache.getNowNext(c.channel.epgId, c.channel.id) // Mocking this for now to show something, ideally we need EpgCache.range()
                    }
                    
                    Surface(
                        onClick = { onChannelSelect(c.channel.id) },
                        modifier = (if (isCurrent) Modifier.focusRequester(focusCur) else Modifier).height(56.dp).fillMaxWidth().onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) {
                                if (e.key.nativeKeyCode == AKey.KEYCODE_DPAD_LEFT) { offsetHours -= 1; true }
                                else if (e.key.nativeKeyCode == AKey.KEYCODE_DPAD_RIGHT) { offsetHours += 1; true }
                                else false
                            } else false
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = C.fill2, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                    ) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            if (epg.isEmpty()) {
                                Text("Program bilgisi yok", Modifier.padding(start = 12.dp), color = LocalContentColor.current.copy(alpha = 0.6f))
                            } else {
                                for (prog in epg) {
                                    Text(programmeHeadline(prog.title), Modifier.padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
