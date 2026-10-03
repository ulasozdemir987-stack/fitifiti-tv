package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.data.xtream.Account
import com.fitifiti.tv.data.xtream.XtreamClient
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.launch
import java.util.UUID

/** Xtream hesabı ekleme/düzenleme. Bilgiler cihazda şifreli saklanır, hiçbir yere gönderilmez (yalnız kullanıcının kendi sunucusuna). */
@Composable
fun LoginScreen(editId: String?, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val app = App.instance
    val existing = remember(editId) { app.accounts.accounts.value.firstOrNull { it.id == editId } }
    var server by remember { mutableStateOf(existing?.server ?: "") }
    var user by remember { mutableStateOf(existing?.username ?: "") }
    var pass by remember { mutableStateOf(existing?.password ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val remote = App.instance.remote
    fun submit(fromPhone: Boolean = false) {
        if (busy) return
        if (server.isBlank() || user.isBlank() || pass.isBlank()) { error = "Sunucu, kullanıcı adı ve şifre gerekli"; if (fromPhone) remote.loginResult(false, error); return }
        busy = true; error = null
        val acc = Account(existing?.id ?: UUID.randomUUID().toString(), XtreamClient.normalizeServer(server), user.trim(), pass, label.trim())
        scope.launch {
            try {
                XtreamClient(app.http, acc).userInfo()
                app.accounts.upsert(acc)
                app.accounts.setActive(acc.id)
                if (fromPhone) remote.loginResult(true)
                onDone()
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() && e is com.fitifiti.tv.data.xtream.XtreamException } ?: "Sunucuya bağlanılamadı. Adresi kontrol et."
                if (fromPhone) remote.loginResult(false, error)
            } finally { busy = false }
        }
    }

    // Telefondan giriş: QR okutulunca telefon bu ekranı görür, bilgiler şifreli gelir
    val active = com.fitifiti.tv.ui.LocalScreenActive.current
    DisposableEffect(active) {
        if (active) com.fitifiti.tv.data.remote.RemoteBus.screen.value = "login"
        onDispose { if (com.fitifiti.tv.data.remote.RemoteBus.screen.value == "login") com.fitifiti.tv.data.remote.RemoteBus.screen.value = "app" }
    }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        com.fitifiti.tv.data.remote.RemoteBus.login.collect { l ->
            server = l.server; user = l.username; pass = l.password; label = l.label
            submit(fromPhone = true)
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.16f), Color.Transparent), radius = 1100f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 40.dp)) {
                Wordmark(40)
                Spacer(Modifier.height(16.dp))
                Text(if (existing == null) "IPTV hesabını bağla" else "Hesabı düzenle", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text("Sağlayıcının verdiği Xtream bilgilerini sağdaki forma ya da telefondan gir. Bilgiler yalnız bu cihazda şifreli saklanır.",
                    style = MaterialTheme.typography.bodyMedium, color = C.muted)
                Spacer(Modifier.height(22.dp))
                // Telefondan giriş: QR büyük (koltuktan okunabilsin)
                val code by App.instance.remote.code.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(remember(code) { App.instance.remote.url }, 220.dp)
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("Telefondan gir", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Telefonun kamerasıyla okut, bilgileri telefonun klavyesiyle yaz.", style = MaterialTheme.typography.bodySmall, color = C.muted)
                        Spacer(Modifier.height(10.dp))
                        RemoteStatus()
                    }
                }
            }
            Column(
                Modifier.width(440.dp).clip(RoundedCornerShape(22.dp)).background(C.panel).padding(28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TvTextField(server, { server = it }, "Sunucu adresi", Modifier.focusRequester(first), placeholder = "http://ornek.com:8080", keyboard = KeyboardType.Uri, icon = Icons.Default.Dns)
                TvTextField(user, { user = it }, "Kullanıcı adı", icon = Icons.Default.Person)
                TvTextField(pass, { pass = it }, "Şifre", password = true, icon = Icons.Default.Lock)
                TvTextField(label, { label = it }, "Hesap adı (isteğe bağlı)", placeholder = "Ev", imeAction = ImeAction.Done, onDone = { submit() })
                if (error != null) Text(error!!, color = C.danger, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Btn(if (busy) "Bağlanıyor…" else "Bağlan", { submit() }, enabled = !busy)
                    if (onCancel != null) Btn("Vazgeç", onCancel, kind = BtnKind.Secondary)
                }
            }
        }
    }
}
