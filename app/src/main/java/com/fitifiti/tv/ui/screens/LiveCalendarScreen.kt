package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.data.xtream.EpgItem
import com.fitifiti.tv.domain.LiveChannel
import com.fitifiti.tv.domain.LiveManager
import com.fitifiti.tv.domain.ParsedMatch
import com.fitifiti.tv.domain.parseMatch
import com.fitifiti.tv.domain.programmeHeadline
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.hhmm
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.data.local.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarEvent(
    val channel: LiveChannel,
    val item: EpgItem,
    val match: ParsedMatch? = null
)

@Composable
fun LiveCalendarScreen(onBack: () -> Unit = {}) {
    val allChannels by LiveManager.getVisibleChannels().collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    
    val focusTabs = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusTabs.requestFocus() } }

    Column(Modifier.fillMaxSize().background(C.bg).padding(top = 40.dp)) {
        Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TabBtn("Bugün", tab == 0, Modifier.focusRequester(focusTabs)) { tab = 0 }
            TabBtn("Yarın", tab == 1) { tab = 1 }
        }
        
        Spacer(Modifier.height(24.dp))
        
        var matches by remember { mutableStateOf<List<Pair<String, List<CalendarEvent>>>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        
        LaunchedEffect(allChannels, tab) {
            loading = true
            val t = System.currentTimeMillis() + (tab * 86400000L)
            val cal = Calendar.getInstance().apply { timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            val from = cal.timeInMillis
            val to = from + 86400000L
            
            val epgIds = allChannels.mapNotNull { it.channel.epgId }.distinct()
            val epgs = EpgCache.range(epgIds, from, to)
            
            val evs = mutableListOf<CalendarEvent>()
            for (c in allChannels) {
                if (c.channel.epgId == null) continue
                val items = epgs[c.channel.epgId] ?: continue
                for (it in items) {
                    val m = parseMatch(it.title)
                    if (m != null) evs.add(CalendarEvent(c, it, m))
                }
            }
            
            // Group by league
            matches = evs.groupBy { it.match?.league ?: "Diğer" }
                .map { it.key to it.value.sortedBy { e -> e.item.start } }
                .sortedBy { if (it.first == "Süper Lig") "" else it.first }
                
            loading = false
        }
        
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Yükleniyor...", color = C.muted, style = MaterialTheme.typography.titleLarge) }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 64.dp, start = 48.dp, end = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(matches) { (league, evs) ->
                    Text(league, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = C.primary, modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (ev in evs) {
                            EventRow(ev)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(ev: CalendarEvent) {
    Surface(
        onClick = { 
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val rem = ReminderEntity("-", App.instance.user.profileId.value, programmeHeadline(ev.item.title), ev.channel.channel.id, ev.item.start, ev.item.end)
                App.instance.db.reminders().upsert(rem)
            }
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.panel, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hhmm(ev.item.start), fontWeight = FontWeight.Bold, color = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.width(60.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(programmeHeadline(ev.item.title), fontWeight = FontWeight.Bold)
                if (ev.match?.round != null) Text(ev.match.round, fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(16.dp))
            Text(ev.channel.name, fontSize = 14.sp, color = LocalContentColor.current.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun TabBtn(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.padding(vertical = 8.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) C.primary else C.panel, focusedContainerColor = Color.White, contentColor = if (selected) Color.Black else Color.White, focusedContentColor = Color.Black)
    ) {
        Text(text, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
    }
}
