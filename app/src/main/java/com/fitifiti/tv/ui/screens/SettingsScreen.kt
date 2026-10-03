package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.App
import com.fitifiti.tv.BuildConfig
import com.fitifiti.tv.data.catalog.EpgCache
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onProfiles: () -> Unit, onEditAccount: (String) -> Unit, onAddAccount: () -> Unit) {
    val app = App.instance
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val accounts by app.accounts.accounts.collectAsStateWithLifecycle()
    val activeId by app.accounts.activeId.collectAsStateWithLifecycle()
    val active = accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
    val profileId by app.user.profileId.collectAsStateWithLifecycle()
    val profile by produceState<com.fitifiti.tv.data.local.ProfileEntity?>(null, profileId) { value = app.db.profiles().get(profileId) }
    var editProfile by remember { mutableStateOf(false) }
    var tmdb by remember { mutableStateOf(s.tmdbKey) }
    var tmdbState by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var pair by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 100.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Ayarlar", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(bottom = 12.dp)) }

        item { Section("Profil") }
        item { SettingRow("Profil değiştir", profile?.name, onClick = onProfiles, icon = Icons.Default.People, modifier = Modifier.fillMaxWidth(0.7f)) }
        if (profile != null) item { SettingRow("Profili düzenle", hint = "Ad, renk ve PIN", onClick = { editProfile = true }, icon = Icons.Default.Edit, modifier = Modifier.fillMaxWidth(0.7f)) }

        item { Section("Telefon kumandası") }
        item { SettingRow("Telefonu kumanda yap", hint = "QR'ı okut; yön tuşları, oynatma ve klavye telefondan. Aynı Wi-Fi gerekmez.", onClick = { pair = true }, icon = Icons.Default.PhoneAndroid, modifier = Modifier.fillMaxWidth(0.7f)) }

        item { Section("Oynatma") }
        item { SettingRow("Fragmanı otomatik oynat", if (s.trailerAutoplay) "Açık" else "Kapalı", hint = "Film ve dizi sayfasında", onClick = { app.settings.update { it.copy(trailerAutoplay = !it.trailerAutoplay) } }, icon = Icons.Default.Movie, modifier = Modifier.fillMaxWidth(0.7f)) }
        item { SettingRow("Fragman sesi", if (s.trailerSound) "Açık" else "Kapalı", onClick = { app.settings.update { it.copy(trailerSound = !it.trailerSound) } }, icon = Icons.Default.VolumeUp, modifier = Modifier.fillMaxWidth(0.7f)) }
        item { SettingRow("Sonraki bölümü otomatik oynat", if (s.autoNext) "Açık" else "Kapalı", onClick = { app.settings.update { it.copy(autoNext = !it.autoNext) } }, icon = Icons.Default.SkipNext, modifier = Modifier.fillMaxWidth(0.7f)) }
        item { SettingRow("“Girişi atla” düğmesi", if (s.skipIntro) "Açık" else "Kapalı", hint = "Bölümün ilk dakikalarında görünür, 85 sn ileri sarar", onClick = { app.settings.update { it.copy(skipIntro = !it.skipIntro) } }, icon = Icons.Default.FastForward, modifier = Modifier.fillMaxWidth(0.7f)) }
        item {
            val langs = listOf("tr" to "Türkçe", "en" to "İngilizce", "off" to "Kapalı")
            SettingRow("Tercih edilen altyazı", langs.firstOrNull { it.first == s.subtitleLang }?.second, hint = "Akışta varsa açılışta seçilir", onClick = {
                val i = langs.indexOfFirst { it.first == s.subtitleLang }
                app.settings.update { it.copy(subtitleLang = langs[(i + 1) % langs.size].first) }
            }, icon = Icons.Default.ClosedCaption, modifier = Modifier.fillMaxWidth(0.7f))
        }
        item {
            val sizes = listOf(0.85f to "Küçük", 1f to "Orta", 1.25f to "Büyük", 1.5f to "Çok büyük")
            SettingRow("Altyazı boyutu", sizes.minByOrNull { kotlin.math.abs(it.first - s.subtitleScale) }?.second, onClick = {
                val i = sizes.indexOfFirst { kotlin.math.abs(it.first - s.subtitleScale) < 0.01f }
                app.settings.update { it.copy(subtitleScale = sizes[(i + 1) % sizes.size].first) }
            }, icon = Icons.Default.FormatSize, modifier = Modifier.fillMaxWidth(0.7f))
        }

        item { Section("Görseller ve bilgiler (TMDB)") }
        item {
            Column(Modifier.fillMaxWidth(0.7f)) {
                Text("İsteğe bağlı: sahne görselleri, logolar, oyuncular ve bölüm görselleri zaten fıtıfıtı sunucusu üzerinden gelir. Kendi TMDB API anahtarını girersen istekler doğrudan TMDB'ye gider (themoviedb.org → Ayarlar → API).",
                    style = MaterialTheme.typography.bodyMedium, color = C.muted)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvTextField(tmdb, { tmdb = it.trim(); tmdbState = null }, "API anahtarı (v3)", Modifier.weight(1f), imeAction = ImeAction.Done)
                    Btn("Kaydet", {
                        tmdbState = "Kontrol ediliyor…"
                        scope.launch {
                            val ok = tmdb.isBlank() || app.art.validate(tmdb)
                            if (ok) { app.settings.update { it.copy(tmdbKey = tmdb) }; tmdbState = if (tmdb.isBlank()) "Kaldırıldı" else "Kaydedildi ✓" }
                            else tmdbState = "Anahtar geçersiz"
                        }
                    }, kind = BtnKind.Secondary)
                }
                tmdbState?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.contains("geçersiz")) C.danger else C.muted) }
            }
        }

        item { Section("IPTV hesabı") }
        if (active != null) item { SettingRow("Hesap", active.label.ifBlank { active.username }, hint = active.server, onClick = { onEditAccount(active.id) }, icon = Icons.Default.Dns, modifier = Modifier.fillMaxWidth(0.7f)) }
        if (accounts.size > 1) item {
            SettingRow("Hesap değiştir", "${accounts.size} hesap", onClick = {
                val i = accounts.indexOfFirst { it.id == active?.id }
                app.accounts.setActive(accounts[(i + 1) % accounts.size].id)
                app.catalog.clear(); onProfiles()
            }, icon = Icons.Default.SwapHoriz, modifier = Modifier.fillMaxWidth(0.7f))
        }
        item { SettingRow("Hesap ekle", onClick = onAddAccount, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth(0.7f)) }
        item { SettingRow("Kataloğu yenile", hint = "Sağlayıcıdan film, dizi ve kanal listesini yeniden indir", onClick = { active?.let { EpgCache.clear(); app.catalog.start(it, force = true) } }, icon = Icons.Default.Refresh, modifier = Modifier.fillMaxWidth(0.7f)) }
        if (active != null) item {
            SettingRow(if (confirmRemove) "Emin misin? Hesabı kaldır" else "Hesabı bu cihazdan kaldır", onClick = {
                if (!confirmRemove) confirmRemove = true
                else { app.accounts.remove(active.id); app.catalog.clear(); onProfiles() }
            }, icon = Icons.Default.Delete, modifier = Modifier.fillMaxWidth(0.7f))
        }

        item { Section("Hakkında") }
        item {
            val up by com.fitifiti.tv.data.update.Updater.state.collectAsStateWithLifecycle()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val (value, hint) = when (val u = up) {
                is com.fitifiti.tv.data.update.UpdateState.Checking -> "Bakılıyor…" to null
                is com.fitifiti.tv.data.update.UpdateState.UpToDate -> "Güncel" to "Şu an ${BuildConfig.VERSION_NAME}"
                is com.fitifiti.tv.data.update.UpdateState.Available -> "Sürüm ${u.info.name} hazır" to "Kurmak için bas"
                is com.fitifiti.tv.data.update.UpdateState.Downloading -> "İndiriliyor %${(u.progress * 100).toInt()}" to null
                is com.fitifiti.tv.data.update.UpdateState.Installing -> "Kuruluyor…" to "Açılan ekranda “Yükle”ye bas"
                is com.fitifiti.tv.data.update.UpdateState.NeedsPermission -> "İzin gerekli" to "fıtıfıtı için “Bilinmeyen uygulamaları yükle” iznini aç, sonra tekrar bas"
                is com.fitifiti.tv.data.update.UpdateState.Failed -> "Olmadı" to u.message
                else -> BuildConfig.VERSION_NAME to "Yeni sürüm var mı bak"
            }
            SettingRow("Güncellemeler", value, hint = hint, onClick = {
                when (val u = up) {
                    is com.fitifiti.tv.data.update.UpdateState.Available -> com.fitifiti.tv.data.update.Updater.install(ctx, u.info)
                    is com.fitifiti.tv.data.update.UpdateState.NeedsPermission -> com.fitifiti.tv.data.update.Updater.install(ctx, u.info)
                    is com.fitifiti.tv.data.update.UpdateState.Failed -> if (u.info != null) com.fitifiti.tv.data.update.Updater.install(ctx, u.info) else com.fitifiti.tv.data.update.Updater.check(manual = true)
                    else -> com.fitifiti.tv.data.update.Updater.check(manual = true)
                }
            }, icon = Icons.Default.SystemUpdate, modifier = Modifier.fillMaxWidth(0.7f))
        }
        item { Text("fıtıfıtı · sürüm ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = C.muted) }
        item { Text("İçerikler kendi IPTV sağlayıcından gelir; uygulama hiçbir içerik barındırmaz.", style = MaterialTheme.typography.bodySmall, color = C.faint) }
    }

    if (pair) RemotePairDialog { pair = false }
    if (editProfile && profile != null) ProfileEditDialog(profile, profile!!.accountId, canCancel = true) { editProfile = false }
}

@Composable
private fun Section(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = C.muted, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
