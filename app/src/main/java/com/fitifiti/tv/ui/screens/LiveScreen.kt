package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.xtream.Channel
import com.fitifiti.tv.domain.categoryLabel
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C

private const val RECENT = "__recent"
private const val ALL = "__all"

/** Canlı TV: solda kategoriler (odakla değişir), sağda şimdiki programıyla kanal kartları */
@Composable
fun LiveScreen() {
    val app = App.instance
    val actions = LocalActions.current
    val cat by app.catalog.catalog.collectAsStateWithLifecycle()
    val recent by app.user.recentChannels.collectAsStateWithLifecycle()
    val cats = remember(cat) {
        val used = cat.channels.mapNotNull { it.categoryId }.toSet()
        cat.liveCats.filter { it.id in used }
    }
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (recent.isNotEmpty()) RECENT else cats.firstOrNull()?.id ?: ALL) }
    val channels: List<Channel> = remember(selected, cat, recent) {
        when (selected) {
            RECENT -> recent.mapNotNull { cat.channelById[it.channelId] }
            ALL -> cat.channels
            else -> cat.channels.filter { it.categoryId == selected }
        }
    }
    val ids = channels.map { it.id }
    val grid = rememberLazyGridState()
    LaunchedEffect(selected) { grid.scrollToItem(0) }

    Row(Modifier.fillMaxSize().padding(top = 92.dp)) {
        LazyColumn(Modifier.width(280.dp).fillMaxHeight(), contentPadding = PaddingValues(start = 36.dp, end = 12.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (recent.isNotEmpty()) item { CatItem("Son izlenenler", selected == RECENT) { selected = RECENT } }
            item { CatItem("Tüm kanallar", selected == ALL) { selected = ALL } }
            items(cats, key = { it.id }) { c -> CatItem(categoryLabel(c.name), selected == c.id) { selected = c.id } }
        }
        LazyVerticalGrid(
            state = grid, columns = GridCells.Adaptive(220.dp), modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(start = 20.dp, end = 48.dp, bottom = 80.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (channels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Bu kategoride kanal yok") }
            items(channels, key = { it.id }) { ch -> ChannelCard(ch, { actions.playChannel(ch.id, ids) }, width = 220.dp) }
        }
    }
}

/** Sol menü öğesi: seçili = beyaz yazı + solda ince mor çizgi; odakta zemin (kategori odakla seçilir) */
@Composable
private fun CatItem(label: String, selected: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect, modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused && !selected) onSelect() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = C.fill3, contentColor = if (selected) Color.White else C.muted, focusedContentColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            Box(Modifier.padding(start = 4.dp).width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) C.primary else Color.Transparent))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
