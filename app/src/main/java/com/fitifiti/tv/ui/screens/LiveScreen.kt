package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.data.xtream.EpgItem
import com.fitifiti.tv.domain.LiveChannel
import com.fitifiti.tv.domain.LiveManager
import com.fitifiti.tv.domain.ParsedMatch
import com.fitifiti.tv.domain.parseMatch
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.Route
import com.fitifiti.tv.ui.components.ChannelCard
import com.fitifiti.tv.ui.player.PillBtn
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun LiveScreen() {
    val nav = LocalActions.current
    val allChannels by LiveManager.getVisibleChannels().collectAsStateWithLifecycle(emptyList())
    val lists by LiveManager.getVisibleLists().collectAsStateWithLifecycle(emptyList())
    
    var matches by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var movies by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var series by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    
    LaunchedEffect(allChannels) {
        if (allChannels.isEmpty()) return@LaunchedEffect
        val epgIds = allChannels.mapNotNull { it.channel.epgId }.distinct()
        val now = System.currentTimeMillis()
        val epgs = EpgCache.range(epgIds, now - 3600000L, now + 86400000L)
        
        val newMatches = mutableListOf<LiveChannel>()
        val newMovies = mutableListOf<LiveChannel>()
        val newSeries = mutableListOf<LiveChannel>()
        
        for (c in allChannels) {
            val items = epgs[c.channel.epgId] ?: continue
            val current = items.find { it.start <= now && it.end > now }
            if (current != null) {
                val title = current.title.lowercase()
                if (parseMatch(title) != null) newMatches.add(c)
                else if (title.contains("film") || title.contains("sinema")) newMovies.add(c)
                else if (title.contains("dizi")) newSeries.add(c)
            }
        }
        
        matches = newMatches
        movies = newMovies
        series = newSeries
    }
    
    val hero = matches.firstOrNull() ?: allChannels.firstOrNull()
    
    LazyColumn(Modifier.fillMaxSize().padding(top = 20.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillBtn("Kanallar", { nav.nav.push(Route.LivePlayer(-1)) })
                PillBtn("Yayın Akışı", { nav.nav.push(Route.LiveCalendar) })
            }
        }
        
        if (hero != null) {
            item {
                Box(Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 48.dp).clip(RoundedCornerShape(12.dp)).background(C.panel)) {
                    AsyncImage(
                        model = hero.channel.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        alpha = 0.5f
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                        Text(hero.name, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        PillBtn("İzle", { nav.nav.push(Route.LivePlayer(hero.channel.id)) }, primary = true)
                    }
                }
            }
        }
        
        if (matches.isNotEmpty()) {
            item { LiveStrip("Şu an maçlar", matches, nav) }
        }
        
        if (movies.isNotEmpty()) {
            item { LiveStrip("Şu an filmler", movies, nav) }
        }
        
        if (series.isNotEmpty()) {
            item { LiveStrip("Diziler", series, nav) }
        }
        
        item { LiveStrip("Tüm Kanallar", allChannels.take(20), nav) }
    }
}

@Composable
private fun LiveStrip(title: String, channels: List<LiveChannel>, nav: com.fitifiti.tv.ui.Actions) {
    Column(Modifier.padding(top = 32.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(channels, key = { it.channel.id }) { c ->
                ChannelCard(c.channel, onClick = { nav.nav.push(Route.LivePlayer(c.channel.id)) }, modifier = Modifier.width(220.dp))
            }
        }
    }
}
