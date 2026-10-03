package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ProfileEntity
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.launch
import java.util.Calendar

/** "Kim izliyor?" — hesabın yerel profilleri. Her profilin kendi izleme geçmişi ve Listem'i var. */
@Composable
fun ProfilesScreen(onSelect: (ProfileEntity) -> Unit, onAddAccount: () -> Unit, onEditAccount: (String) -> Unit) {
    val app = App.instance
    val account by app.accounts.activeId.collectAsStateWithLifecycle()
    val accounts by app.accounts.accounts.collectAsStateWithLifecycle()
    val acc = accounts.firstOrNull { it.id == account } ?: accounts.firstOrNull() ?: return
    val profiles by remember(acc.id) { app.db.profiles().observe(acc.id) }.collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pair by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    // Hiç profil yoksa doğrudan oluşturma penceresi
    LaunchedEffect(profiles) { if (profiles?.isEmpty() == true) creating = true }
    LaunchedEffect(profiles?.size) { if (!profiles.isNullOrEmpty()) runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.primary.copy(alpha = 0.12f), Color.Transparent), radius = 1300f)))
        Column(Modifier.fillMaxSize().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(44)
            Spacer(Modifier.weight(1f))
            Text(if (manage) "Profilleri düzenle" else greeting(), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(horizontal = 48.dp)) {
                val list = profiles.orEmpty()
                items(list, key = { it.id }) { p ->
                    ProfileTile(p, manage, Modifier.then(if (p == list.first()) Modifier.focusRequester(firstFocus) else Modifier)) {
                        when {
                            manage -> editing = p
                            !p.pin.isNullOrEmpty() -> pinFor = p
                            else -> onSelect(p)
                        }
                    }
                }
                if (list.size < 8) item { AddTile { creating = true } }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Btn(if (manage) "Bitti" else "Profilleri düzenle", { manage = !manage }, kind = BtnKind.Secondary, icon = if (manage) null else Icons.Default.Edit)
                Btn("Hesap: ${acc.label.ifBlank { acc.username }}", { onEditAccount(acc.id) }, kind = BtnKind.Ghost)
                if (accounts.size > 1) Btn("Hesap değiştir", {
                    val i = accounts.indexOfFirst { it.id == acc.id }
                    app.accounts.setActive(accounts[(i + 1) % accounts.size].id)
                }, kind = BtnKind.Ghost)
                Btn("Hesap ekle", onAddAccount, kind = BtnKind.Ghost, icon = Icons.Default.Add)
                Btn("Telefon kumandası", { pair = true }, kind = BtnKind.Ghost, icon = Icons.Default.PhoneAndroid)
            }
        }
    }

    if (creating || editing != null) ProfileEditDialog(
        profile = editing, accountId = acc.id, canCancel = !profiles.isNullOrEmpty(),
        onDismiss = { creating = false; editing = null },
    )
    if (pair) RemotePairDialog { pair = false }
    pinFor?.let { p ->
        PinDialog(title = "${p.name} için PIN", onDismiss = { pinFor = null }) { pin ->
            if (pin == p.pin) { pinFor = null; onSelect(p); true } else false
        }
    }
}

private fun greeting(): String {
    val c = Calendar.getInstance()
    val h = c.get(Calendar.HOUR_OF_DAY)
    val weekend = c.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
    return when {
        h < 5 -> "Gece yarısı… bir bölüm daha mı?"
        h < 12 -> "Günaydın, kim izliyor?"
        h < 18 -> if (weekend) "Hafta sonu keyfi, kim izliyor?" else "Kim izliyor?"
        c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY -> "Cuma akşamı, film gecesi mi?"
        else -> "İyi akşamlar, kim izliyor?"
    }
}

@Composable
private fun ProfileTile(p: ProfileEntity, manage: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick, modifier = Modifier.size(170.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(30.dp))),
        ) {
            Box {
                Avatar(p.name, p.avatar, 170.dp)
                if (manage) Box(Modifier.matchParentSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, null, Modifier.size(40.dp), tint = Color.White) }
                else if (!p.pin.isNullOrEmpty()) Icon(Icons.Default.Lock, null, Modifier.align(Alignment.BottomEnd).padding(12.dp).size(22.dp), tint = Color(0xCCFFFFFF))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(p.name, style = MaterialTheme.typography.titleLarge, color = if (focused) Color.White else C.muted, maxLines = 1)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick, modifier = Modifier.size(170.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.fill1, focusedContainerColor = C.fill3),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(
                border = Border(androidx.compose.foundation.BorderStroke(1.dp, C.line), shape = RoundedCornerShape(30.dp)),
                focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(30.dp)),
            ),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, Modifier.size(56.dp), tint = Color(0xB3FFFFFF)) } }
        Spacer(Modifier.height(16.dp))
        Text("Profil ekle", style = MaterialTheme.typography.titleLarge, color = if (focused) Color.White else C.muted)
    }
}

@Composable
fun ProfileEditDialog(profile: ProfileEntity?, accountId: String, canCancel: Boolean, onDismiss: () -> Unit) {
    val app = App.instance
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var avatar by remember { mutableIntStateOf(profile?.avatar ?: (0..7).random()) }
    var pin by remember { mutableStateOf(profile?.pin ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun save() {
        val n = name.trim().take(20)
        if (n.isEmpty()) return
        scope.launch {
            app.db.profiles().upsert((profile ?: ProfileEntity(accountId = accountId, name = n)).copy(name = n, avatar = avatar, pin = pin.filter { it.isDigit() }.take(4).ifEmpty { null }))
            onDismiss()
        }
    }
    Dialog(onDismissRequest = { if (canCancel) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(560.dp).clip(RoundedCornerShape(22.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(22.dp)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (profile == null) "Yeni profil" else "Profili düzenle", style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(name.ifBlank { "?" }, avatar, 88.dp)
                Spacer(Modifier.width(20.dp))
                TvTextField(name, { name = it.take(20) }, "Ad", Modifier.weight(1f).focusRequester(fr), imeAction = ImeAction.Done, onDone = {})
            }
            Text("Renk", style = MaterialTheme.typography.labelMedium, color = C.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AVATAR_COLORS.indices.forEach { i ->
                    Surface(
                        onClick = { avatar = i }, modifier = Modifier.size(44.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(androidx.compose.foundation.BorderStroke(if (i == avatar) 3.dp else 0.dp, Color.White), shape = RoundedCornerShape(50)),
                            focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(50)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                    ) { Box(Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(50)).background(Brush.linearGradient(AVATAR_COLORS[i]))) }
                }
            }
            TvTextField(pin, { v -> pin = v.filter { it.isDigit() }.take(4) }, "PIN (isteğe bağlı, 4 rakam)", keyboard = KeyboardType.NumberPassword, password = true, imeAction = ImeAction.Done, onDone = { save() })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("Kaydet", { save() }, enabled = name.isNotBlank())
                if (canCancel) Btn("Vazgeç", onDismiss, kind = BtnKind.Secondary)
                if (profile != null) Btn(if (confirmDelete) "Emin misin? Sil" else "Profili sil", {
                    if (!confirmDelete) confirmDelete = true
                    else scope.launch {
                        app.db.progress().clear(profile.id)
                        app.db.profiles().delete(profile.id)
                        onDismiss()
                    }
                }, kind = BtnKind.Ghost)
            }
        }
    }
}

/** Rakam tuşlu PIN penceresi (kumandanın rakam tuşları da çalışır) */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    fun type(d: String) {
        wrong = false
        pin = (pin + d).take(4)
        if (pin.length == 4) { if (!onSubmit(pin)) { wrong = true; pin = "" } }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(C.panel).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { i -> Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(if (i < pin.length) Color.White else C.fill3)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (wrong) "PIN yanlış" else " ", color = C.danger, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "")
            keys.chunked(3).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEachIndexed { c, k ->
                        if (k.isEmpty()) Spacer(Modifier.size(72.dp, 56.dp))
                        else Surface(
                            onClick = { if (k == "⌫") pin = pin.dropLast(1) else type(k) },
                            modifier = Modifier.size(72.dp, 56.dp).then(if (r == 0 && c == 0) Modifier.focusRequester(fr) else Modifier)
                                .onKeyDigit { type(it) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = C.fill2, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(k, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) } }
                    }
                }
            }
        }
    }
}

/** Kumandanın rakam tuşları */
fun Modifier.onKeyDigit(onDigit: (String) -> Unit): Modifier = this.then(
    Modifier.onPreviewKeyEventCompat { code ->
        val d = code - android.view.KeyEvent.KEYCODE_0
        if (d in 0..9) { onDigit(d.toString()); true } else false
    }
)
