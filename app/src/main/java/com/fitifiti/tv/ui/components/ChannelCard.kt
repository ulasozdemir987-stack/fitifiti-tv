package com.fitifiti.tv.ui.components

import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.SubcomposeAsyncImage
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.data.catalog.fraction
import com.fitifiti.tv.data.catalog.now
import com.fitifiti.tv.data.xtream.Channel
import com.fitifiti.tv.data.xtream.EpgItem
import com.fitifiti.tv.domain.cleanChannelName
import com.fitifiti.tv.domain.programmeHeadline
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kanal adının kısaltması ("ATV", "KD", "STAR") — logo yoksa */
fun channelMonogram(name: String): String {
    val n = cleanChannelName(name).replace(Regex("(?i)\\b(hd|fhd|uhd|4k|sd|tr|raw)\\b"), "").trim()
    val words = n.split(Regex("[\\s\\-_.]+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "TV"
    if (words.size == 1 || words[0].length <= 4 && words[0].all { it.isUpperCase() || it.isDigit() }) return words[0].take(4).uppercase(Locale("tr", "TR"))
    return words.take(2).joinToString("") { it.take(1) }.uppercase(Locale("tr", "TR"))
}

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier) {
    val fallback: @Composable () -> Unit = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(channelMonogram(channel.name), style = Display.copy(fontSize = 26.sp, color = Color(0xB3FFFFFF))) } }
    if (channel.icon.isNullOrBlank()) Box(modifier) { fallback() }
    else SubcomposeAsyncImage(model = channel.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier, error = { fallback() }, loading = { fallback() })
}

/** Şu an oynayan program (5 dk önbellekli) */
@Composable
fun rememberEpg(channel: Channel): List<EpgItem> {
    val v by produceState(emptyList<EpgItem>(), channel.id) { value = EpgCache.getNowNext(channel.epgId, channel.id) }
    return v
}

private val clock = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
fun hhmm(t: Long): String = clock.format(Date(t))

/** Kanal kartı: logo + ad + şimdiki program + ilerleme (sitedeki Canlı TV Tile) */
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 240.dp, label: String? = null, onFocus: (() -> Unit)? = null) {
    val epg = rememberEpg(channel)
    val now = epg.now()
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).rememberFocus().then(if (onFocus != null) Modifier.focusReport(onFocus = onFocus) else Modifier),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.fill2, focusedContainerColor = C.fill3),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(10.dp))),
        ) {
            Box(Modifier.fillMaxSize()) {
                ChannelLogo(channel, Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp))
                if (label != null) Text(label, Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = Color.White)
                if (now != null) ProgressLine(now.fraction(), Modifier.align(Alignment.BottomCenter).fillMaxWidth(), height = 3.dp, track = Color(0x33FFFFFF))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(cleanChannelName(channel.name), style = MaterialTheme.typography.bodyMedium, color = Color(0xE6FFFFFF), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(now?.let { programmeHeadline(it.title) + " · " + hhmm(it.end) + "'e kadar" } ?: " ", style = MaterialTheme.typography.bodySmall, color = C.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
