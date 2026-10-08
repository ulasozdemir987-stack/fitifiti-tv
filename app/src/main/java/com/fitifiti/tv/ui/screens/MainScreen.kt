package com.fitifiti.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.CatalogStatus
import com.fitifiti.tv.ui.LocalScreenActive
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C

enum class Tab(val label: String) { Home("Keşfet"), Movies("Filmler"), Series("Diziler"), Live("Canlı TV"), Listem("Listem"), Search("Ara"), Settings("Ayarlar") }

/** Üst çubuk görünürlüğü: sekme içeriği aşağı kaydırınca gizlenir */
class TopBarState { var hidden by mutableStateOf(false) }
val LocalTopBar = compositionLocalOf { TopBarState() }

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(onProfiles: () -> Unit, onEditAccount: (String) -> Unit, onAddAccount: () -> Unit, onEditChannels: () -> Unit = {}, startTab: Tab = Tab.Home) {
    val app = App.instance
    var tab by rememberSaveableTab(startTab)
    val bar = remember { TopBarState() }
    val holder = rememberSaveableStateHolder()
    val status by app.catalog.status.collectAsStateWithLifecycle()
    val catalog by app.catalog.catalog.collectAsStateWithLifecycle()
    val tabFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val screenMem = com.fitifiti.tv.ui.LocalFocusMemory.current
    // sekme içeriğinin son odaklanan öğesi (ekran katmanının hafızasına da yazar)
    val contentMem = remember(screenMem) { com.fitifiti.tv.ui.FocusMemory(screenMem) }
    val profileId by app.user.profileId.collectAsStateWithLifecycle()
    val profile by produceState<com.fitifiti.tv.data.local.ProfileEntity?>(null, profileId) { value = app.db.profiles().get(profileId) }
    // Ara simgesine OK: arama sayfası + yazı kutusunda klavye açılır
    var searchKick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tab) { com.fitifiti.tv.data.diag.Diag.lastScreen = "Ana ekran · ${tab.label}" }

    // Geri / ana sayfa: önce sekme değişir, odak yeni seçili sekmeye yeniden bağlanınca verilir. Eskiden odak hemen
    // isteniyordu → hâlâ ESKİ sekmeye bağlı olduğundan oraya gidiyor, "üzerinde durunca açılır" kuralı da eski sekmeyi
    // geri açıyordu (geri tuşu çalışmıyor gibi görünüyordu).
    val scope = rememberCoroutineScope()
    val goHome = {
        tab = Tab.Home; bar.hidden = false
        scope.launch { repeat(3) { kotlinx.coroutines.delay(60); runCatching { tabFocus.requestFocus() } } }
        Unit
    }
    LaunchedEffect(Unit) { com.fitifiti.tv.data.remote.RemoteBus.home.collect { goHome() } }
    BackHandler(enabled = tab != Tab.Home && LocalScreenActive.current) { goHome() }

    if (catalog.isEmpty) {
        when (val s = status) {
            is CatalogStatus.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("Katalog alınamadı", s.message) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("Tekrar dene", { app.accounts.active?.let { app.catalog.start(it, force = true) } })
                        Btn("Hesabı düzenle", { app.accounts.active?.let { onEditAccount(it.id) } }, kind = BtnKind.Secondary)
                        Btn("Profiller", onProfiles, kind = BtnKind.Ghost)
                    }
                }
            }
            else -> LoadingCatalog((s as? CatalogStatus.Loading)?.step ?: "Hazırlanıyor…", (s as? CatalogStatus.Loading)?.progress ?: 0.02f)
        }
        return
    }

    CompositionLocalProvider(LocalTopBar provides bar) {
        Box(Modifier.fillMaxSize()) {
            // Üst çubuktan ↓: odak doğrudan içeriğe (son odaklanan öğeye) iner. Yön araması yalnız alt alta hizalı öğe
            // arar; sağdaki Ara/Ayarlar simgelerinin altında öğe olmayınca (Ayarlar satırları solda) hiç inmiyordu.
            // İçerikte yukarıda gidilecek öğe kalmayınca (ilk şerit) ↑ = üst çubuktaki seçili sekme. Vitrin alanında
            // odaklanabilir öğe olmadığından yön araması üst çubuğu bulamıyordu (gerçek kutuda görüldü: Ayarlar'a çıkılamıyordu).
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(Modifier.fillMaxSize().mainContentFocus(contentFocus).onPreviewKeyEvent { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionUp) {
                    if (!focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)) { bar.hidden = false; runCatching { tabFocus.requestFocus() } }
                    true
                } else false
            }) {
            CompositionLocalProvider(com.fitifiti.tv.ui.LocalFocusMemory provides contentMem) {
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Movies -> MediaScreen("movie")
                    Tab.Series -> MediaScreen("series")
                    Tab.Live -> LiveScreen()
                    Tab.Listem -> ListemScreen()
                    Tab.Search -> SearchScreen(searchKick) { searchKick = 0 }
                    Tab.Settings -> SettingsScreen(onProfiles, onEditAccount, onAddAccount, onEditChannels)
                }
            }
            }
            }
            AnimatedVisibility(!bar.hidden, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                TopBar(tab, { if (it != tab) { tab = it; bar.hidden = false } }, profile, onProfiles, tabFocus, contentFocus, contentMem, onSearch = { tab = Tab.Search; bar.hidden = false; searchKick++ },
                    solid = tab == Tab.Settings || tab == Tab.Listem || tab == Tab.Search || tab == Tab.Live)
            }
        }
    }
}

@Composable
private fun rememberSaveableTab(start: Tab) = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(start) }

@Composable
private fun TopBar(tab: Tab, onTab: (Tab) -> Unit, profile: com.fitifiti.tv.data.local.ProfileEntity?, onProfiles: () -> Unit, tabFocus: FocusRequester, contentFocus: FocusRequester, contentMem: com.fitifiti.tv.ui.FocusMemory, onSearch: () -> Unit, solid: Boolean = false) {
    // çubuktaki her öğeden ↓ = içerikte en son odaklanan öğe, yoksa içeriğin ilk öğesi
    // (FocusProperties en yakın odak hedefine kadar üstteki düğümlerden, her aramada yeniden okunur)
    Box(Modifier.focusProperties { down = contentMem.last ?: contentFocus }.fillMaxWidth().background(if (solid) Brush.verticalGradient(0f to C.bg, 0.82f to C.bg, 1f to C.bg.copy(alpha = 0f)) else Brush.verticalGradient(listOf(C.bg.copy(alpha = 0.85f), Color.Transparent))).padding(horizontal = 48.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AnimatedBrandLogo(width = 110.dp, compact = true, live = true, waveKey = tab, modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Tab.Home, Tab.Movies, Tab.Series, Tab.Live, Tab.Listem).forEach { t ->
                    NavText(t.label, t == tab, { onTab(t) }, if (t == tab) Modifier.focusRequester(tabFocus) else Modifier)
                }
            }
            Spacer(Modifier.weight(1f))
            NavIcon(Icons.Default.Search, "Ara", tab == Tab.Search, onFocusOpen = { onTab(Tab.Search) }, onClick = onSearch)
            NavIcon(Icons.Default.Settings, "Ayarlar", tab == Tab.Settings, onFocusOpen = { onTab(Tab.Settings) }) { onTab(Tab.Settings) }
            Spacer(Modifier.width(10.dp))
            if (profile != null) Surface(
                onClick = onProfiles, modifier = Modifier.size(34.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            ) { Avatar(profile.name, profile.avatar, 34.dp) }
        }
    }
}

/** Sitedeki sekme: ikonsuz metin; seçili = beyaz + altında mor çizgi, odak = hafif zemin */
@Composable
private fun NavText(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    // Sekmeler arasında hızla gezerken her birinin ağır sayfası çizilmesin: sekme üzerinde kısa süre durunca açılır
    LaunchedEffect(focused, selected) { if (focused && !selected) { kotlinx.coroutines.delay(320); onClick() } }
    Surface(
        onClick = onClick, modifier = modifier.padding(horizontal = 2.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = C.fill3, contentColor = if (selected) Color.White else C.muted, focusedContentColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(22.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) C.primary else Color.Transparent))
        }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, label: String, selected: Boolean, onFocusOpen: () -> Unit, onClick: () -> Unit) {
    // Sekmeler gibi: üzerinde kısa süre durunca sayfası açılır (eskiden yalnız OK ile açılıyordu; Ara simgesinde
    // hiçbir şey olmayınca uygulama donmuş sanılıyordu)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, selected) { if (focused && !selected) { kotlinx.coroutines.delay(320); onFocusOpen() } }
    Surface(
        onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp).size(38.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) C.fill3 else Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(19.dp)) } }
}

/** İlk açılış: gerçek adım + ilerleme çizgisi (sonraki açılışlarda önbellek anında gelir) */
@Composable
private fun LoadingCatalog(step: String, progress: Float) {
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MascotLoader(size = 110.dp)
            Spacer(Modifier.height(20.dp))
            Wordmark(56)
            Spacer(Modifier.height(30.dp))
            Box(Modifier.width(420.dp)) { ProgressLine(progress, Modifier.fillMaxWidth(), height = 4.dp, track = C.fill3) }
            Spacer(Modifier.height(16.dp))
            Text(step, style = MaterialTheme.typography.bodyLarge, color = C.muted)
            Spacer(Modifier.height(6.dp))
            Text("İlk açılışta katalog indiriliyor; sonra anında açılır.", style = MaterialTheme.typography.bodySmall, color = C.faint)
        }
    }
}

/** Sekme içeriğinin odak grubu (focusRestorer YOK — bkz. FocusMemory): üst çubuktan ↓ buraya iner */
fun Modifier.mainContentFocus(fr: FocusRequester): Modifier = this.focusRequester(fr).focusGroup()
