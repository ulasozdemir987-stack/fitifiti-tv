package com.fitifiti.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.data.diag.Feedback
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CATEGORIES = listOf("Görüntü / tasarım bozuk", "Takılıyor / donuyor", "Bir şey çalışmıyor", "Öneri")

/** "Sorun bildir" penceresi: ekran görüntüsü önizlemesi, kategori, isteğe bağlı not (telefondan da yazılabilir), Gönder */
@Composable
fun FeedbackDialog(c: Feedback.Capture, onClose: () -> Unit) {
    var category by remember { mutableStateOf(CATEGORIES[0]) }
    var note by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<String?>(null) } // null | gönderiliyor | gitti | olmadı
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    LaunchedEffect(state) { if (state == "gitti") { delay(1400); onClose() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(Modifier.width(820.dp).background(C.panel, RoundedCornerShape(18.dp)).padding(28.dp)) {
            Column(Modifier.width(300.dp)) {
                Text("Sorun bildir", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text("Bu ekranın görüntüsü ve son olaylar geliştiriciye gider. Hesap bilgilerin gitmez.", style = MaterialTheme.typography.bodySmall, color = C.muted)
                Spacer(Modifier.height(14.dp))
                c.preview?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Ne oldu?", style = MaterialTheme.typography.labelLarge, color = C.muted)
                Spacer(Modifier.height(8.dp))
                CATEGORIES.chunked(2).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        row.forEachIndexed { i, cat -> Chip(cat, cat == category, { category = cat }, if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TvTextField(note, { note = it }, "Not (isteğe bağlı)", placeholder = "Kısaca ne oldu? Telefondan da yazabilirsin", imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Btn(if (state == "gönderiliyor") "Gönderiliyor…" else "Gönder", {
                        if (state == "gönderiliyor" || state == "gitti") return@Btn
                        state = "gönderiliyor"
                        scope.launch { state = if (Feedback.send(c, category, note)) "gitti" else "olmadı" }
                    }, icon = Icons.Default.Send)
                    Btn("Vazgeç", onClose, kind = BtnKind.Ghost)
                    when (state) {
                        "gitti" -> Text("Gönderildi, teşekkürler!", color = C.teal)
                        "olmadı" -> Text("Gönderilemedi, tekrar dene", color = C.danger)
                    }
                }
            }
        }
    }
}
