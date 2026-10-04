package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.domain.LiveManager
import com.fitifiti.tv.ui.components.Btn
import com.fitifiti.tv.ui.components.ChannelLogo
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun ChannelEditScreen(onBack: () -> Unit) {
    val channels by LiveManager.getChannels().collectAsStateWithLifecycle(emptyList())
    var movingChannelId by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // TR channels sort button
    var sorting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color(0xFF050508)).padding(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kanal Düzenle", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.weight(1f))
            Btn("Türk Kanallarını Başa Al", onClick = {
                sorting = true
                scope.launch {
                    LiveManager.applyTurkishSort()
                    sorting = false
                }
            })
        }
        Spacer(Modifier.height(20.dp))
        
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(channels, key = { _, ch -> ch.channel.id }) { index, ch ->
                val isMoving = movingChannelId == ch.channel.id
                Surface(
                    onClick = {
                        if (movingChannelId != null) movingChannelId = null
                        else movingChannelId = ch.channel.id
                    },
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                        if (isMoving && e.type == KeyEventType.KeyDown) {
                            when (e.key.nativeKeyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                    scope.launch { LiveManager.moveChannel(ch.channel.id, ch.num - 1) }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    scope.launch { LiveManager.moveChannel(ch.channel.id, ch.num + 1) }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isMoving) C.fill3 else Color.Transparent,
                        focusedContainerColor = if (isMoving) Color(0xFF8B5CF6) else Color.White,
                        contentColor = Color.White,
                        focusedContentColor = if (isMoving) Color.White else Color.Black
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("", Modifier.width(48.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (ch.isHidden) C.muted else Color.Unspecified)
                        ChannelLogo(ch.channel, Modifier.size(48.dp, 36.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)))
                        Spacer(Modifier.width(16.dp))
                        Text(ch.name, fontSize = 18.sp, color = if (ch.isHidden) C.muted else Color.Unspecified, textDecoration = if (ch.isHidden) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        Spacer(Modifier.weight(1f))
                        if (isMoving) {
                            Text("Yukarı/Aşağı tuşlarıyla taşı, kaydetmek için OK'e bas", fontSize = 14.sp, color = C.muted)
                        } else {
                            Btn(if (ch.isHidden) "Göster" else "Gizle", onClick = {
                                val c = App.instance.user.channelConfigs.value.find { it.channelId == ch.channel.id }
                                App.instance.user.upsertChannelConfig(
                                    com.fitifiti.tv.data.local.ChannelConfigEntity(
                                        profileId = App.instance.user.profileId.value,
                                        channelId = ch.channel.id,
                                        isHidden = !(c?.isHidden ?: false),
                                        customName = c?.customName,
                                        sortOrder = c?.sortOrder ?: ch.channel.num,
                                        customList = c?.customList
                                    )
                                )
                            }, kind = com.fitifiti.tv.ui.components.BtnKind.Secondary)
                        }
                    }
                }
            }
        }
    }
}
