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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.graphicsLayer
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

enum class Tab(val label: String) { Home("Ana sayfa"), Movies("Filmler"), Series("Diziler"), Live("Canlı TV"), Guide("Yayın akışı"), Listem("Listem"), Search("Ara"), Settings("Ayarlar") }

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
    // odak kurtarmada son çare: sol menüdeki seçili sekme (her zaman yerleşik ve görünür)
    DisposableEffect(screenMem) { screenMem?.fallback = tabFocus; onDispose { if (screenMem?.fallback === tabFocus) screenMem.fallback = null } }
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

    var railOpen by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalTopBar provides bar) {
        Box(Modifier.fillMaxSize()) {
            // İçerikte solda gidilecek öğe kalmayınca ← = sol menüdeki seçili sekme; menüden → = içerikte en son
            // odaklanan öğe (yön araması menünün sağında hizalı öğe bulamayabiliyor).
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(Modifier.fillMaxSize().padding(start = RailInset).mainContentFocus(contentFocus).onPreviewKeyEvent { e ->
                if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionLeft) {
                    if (!focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)) runCatching { tabFocus.requestFocus() }
                    true
                } else false
            }) {
            CompositionLocalProvider(com.fitifiti.tv.ui.LocalFocusMemory provides contentMem) {
            // Not: sekme geçişine saydamlık katmanı (graphicsLayer alpha) KONMAZ — Canlı TV önizlemesindeki video yüzeyi
            // (SurfaceView) böyle bir katmanın içinde görüntü vermiyor (kutuda denendi: ses var, görüntü siyah).
            Box(Modifier.fillMaxSize()) {
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Movies -> MediaScreen("movie")
                    Tab.Series -> MediaScreen("series")
                    Tab.Live -> LiveScreen()
                    Tab.Guide -> GuideScreen()
                    Tab.Listem -> ListemScreen()
                    Tab.Search -> SearchScreen(searchKick) { searchKick = 0 }
                    Tab.Settings -> SettingsScreen(onProfiles, onEditAccount, onAddAccount, onEditChannels)
                }
            }
            }
            }
            }
            AnimatedVisibility(!bar.hidden, modifier = Modifier.align(Alignment.TopEnd), enter = fadeIn(), exit = fadeOut()) {
                CornerClock(Modifier.padding(top = 18.dp, end = 40.dp))
            }
            val railItems = remember {
                listOf(
                    RailItem(Tab.Search.name, Icons.Default.Search, "Ara"),
                    RailItem(Tab.Home.name, Icons.Outlined.Home, "Ana sayfa"),
                    RailItem(Tab.Live.name, Icons.Outlined.LiveTv, "Canlı TV"),
                    RailItem(Tab.Guide.name, Icons.Outlined.CalendarMonth, "Yayın akışı"),
                    RailItem(Tab.Movies.name, Icons.Outlined.Movie, "Filmler"),
                    RailItem(Tab.Series.name, Icons.Outlined.VideoLibrary, "Diziler"),
                    RailItem(Tab.Listem.name, Icons.Outlined.BookmarkBorder, "Listem"),
                    RailItem(Tab.Settings.name, Icons.Default.Settings, "Ayarlar"),
                )
            }
            // menü genişleyince arkadaki içerik soldan kararır
            val scrim by androidx.compose.animation.core.animateFloatAsState(if (railOpen) 1f else 0f, androidx.compose.animation.core.tween(220), label = "scrim")
            if (scrim > 0.01f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }
                .background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.82f), 0.35f to Color.Black.copy(alpha = 0.55f), 0.7f to Color.Transparent)))
            NavRail(
                railItems, tab.name,
                onSelect = { k -> val t = Tab.valueOf(k); if (t != tab) { tab = t; bar.hidden = false }; if (t == Tab.Search) searchKick++ },
                selectedFocus = tabFocus,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)
                    .focusProperties {
                        right = contentMem.last ?: contentFocus
                        // yön tuşuyla menüye girilince her zaman seçili sekmeye (aynı hizadaki öğeye değil → sayfa değişmesin)
                        enter = { tabFocus }
                    }.focusGroup(),
                profile = profile?.let { p -> { Avatar(p.name, p.avatar, 34.dp) } },
                profileName = profile?.name,
                onProfile = onProfiles,
                onExpandedChange = { railOpen = it },
            )
        }
    }
}

@Composable
private fun rememberSaveableTab(start: Tab) = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(start) }

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
