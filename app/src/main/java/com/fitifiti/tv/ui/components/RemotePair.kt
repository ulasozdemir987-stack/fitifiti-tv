package com.fitifiti.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.ui.theme.C
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

/** QR kod (zxing): beyaz zemin üzerinde siyah modüller, keskin büyütme */
@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(text) {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        val b = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until m.width) for (y in 0 until m.height) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        b.asImageBitmap()
    }
    Image(bmp, contentDescription = "QR kod", filterQuality = FilterQuality.None,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp))
}

/** Telefonun bağlı olup olmadığı */
@Composable
fun RemoteStatus(modifier: Modifier = Modifier) {
    val peers by App.instance.remote.peers.collectAsStateWithLifecycle()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (peers > 0) Color(0xFF34D399) else C.faint))
        Spacer(Modifier.width(8.dp))
        Text(if (peers > 0) "Telefon bağlı" else "Telefon bekleniyor", style = MaterialTheme.typography.bodySmall, color = if (peers > 0) Color(0xFF6EE7B7) else C.muted)
    }
}

/** QR + açıklama kartı (giriş ekranında ve kumanda penceresinde) */
@Composable
fun RemoteQrCard(title: String, hint: String, modifier: Modifier = Modifier, qrSize: Dp = 170.dp) {
    val code by App.instance.remote.code.collectAsStateWithLifecycle()
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(C.fill1).border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        QrCode(remember(code) { App.instance.remote.url }, qrSize)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.widthIn(max = 300.dp)) {
            Icon(Icons.Default.PhoneAndroid, null, Modifier.size(26.dp), tint = C.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = C.muted)
            Spacer(Modifier.height(10.dp))
            RemoteStatus()
        }
    }
}

/** "Telefonu kumanda yap" penceresi */
@Composable
fun RemotePairDialog(onDismiss: () -> Unit) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { f.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(C.panel).padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Telefonu kumanda yap", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("Telefonun kamerasıyla okut. Aynı Wi-Fi'da olmanız gerekmez.", style = MaterialTheme.typography.bodyMedium, color = C.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            val code by App.instance.remote.code.collectAsStateWithLifecycle()
            QrCode(remember(code) { App.instance.remote.url }, 220.dp)
            Spacer(Modifier.height(14.dp))
            RemoteStatus()
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("Tamam", onDismiss, Modifier.focusRequester(f))
                Btn("Yeni kod", { App.instance.remote.regenerate() }, kind = BtnKind.Ghost)
            }
        }
    }
}
