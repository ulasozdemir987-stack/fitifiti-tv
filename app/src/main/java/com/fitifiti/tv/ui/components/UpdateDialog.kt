package com.fitifiti.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.BuildConfig
import com.fitifiti.tv.data.update.UpdateInfo
import com.fitifiti.tv.data.update.UpdateState
import com.fitifiti.tv.data.update.Updater
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay

/** "Güncelleme var" penceresi: Kur → indirme çizgisi → Android'in kurulum onayı */
@Composable
fun UpdateDialog(info: UpdateInfo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { f.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(560.dp).background(C.panel, RoundedCornerShape(18.dp)).padding(32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Güncelleme var", style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                    Text("Sürüm ${info.name} · şu an ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = C.muted)
                }
            }
            info.notes?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(14.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = C.muted, maxLines = 3) }
            Spacer(Modifier.height(22.dp))
            when (val s = state) {
                is UpdateState.Downloading -> {
                    ProgressLine(s.progress, Modifier.fillMaxWidth(), height = 5.dp, track = C.fill3)
                    Spacer(Modifier.height(10.dp))
                    Text("İndiriliyor… %${(s.progress * 100).toInt()}", color = C.muted)
                }
                is UpdateState.Installing -> Text("Kurulum ekranı açılıyor… “Yükle”ye bas.", color = C.muted)
                is UpdateState.NeedsPermission -> {
                    Text("Açılan ayarda fıtıfıtı için “Bilinmeyen uygulamaları yükle” iznini aç, sonra geri gel.", color = C.muted)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("Tekrar dene", { Updater.install(ctx, info) }, Modifier.focusRequester(f), icon = Icons.Default.SystemUpdate)
                        Btn("Sonra", onClose, kind = BtnKind.Secondary)
                    }
                }
                is UpdateState.Failed -> {
                    Text("Olmadı: ${s.message}", color = C.danger)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("Tekrar dene", { Updater.install(ctx, info) }, Modifier.focusRequester(f), icon = Icons.Default.SystemUpdate)
                        Btn("Sonra", onClose, kind = BtnKind.Secondary)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn("Kur", { Updater.install(ctx, info) }, Modifier.focusRequester(f), icon = Icons.Default.SystemUpdate)
                    Btn("Sonra", onClose, kind = BtnKind.Secondary)
                }
            }
        }
    }
}
