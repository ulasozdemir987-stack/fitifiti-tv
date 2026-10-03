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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.fitifiti.tv.App
import com.fitifiti.tv.data.catalog.CatalogStatus
import com.fitifiti.tv.ui.LocalScreenActive
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C

enum class Tab(val label: String) { Home("Ana Sayfa"), Movies("Filmler"), Series("Diziler"), Live("Canlı TV"), Listem("Listem"), Search("Ara"), Settings("Ayarlar") }

/** Üst çubuk görünürlüğü: sekme içeriği aşağı kaydırınca gizlenir */
class TopBarState { var hidden by mutableStateOf(false) }
val LocalTopBar = compositionLocalOf { TopBarState() }

@Composable
fun MainScreen(onProfiles: () -> Unit, onEditAccount: (String) -> Unit, onAddAccount: () -> Unit) {
    val app = App.instance
    var tab by rememberSaveableTab()
    val bar = remember { TopBarState() }
    val holder = rememberSaveableStateHolder()
    val status by app.catalog.status.collectAsStateWithLifecycle()
    val catalog by app.catalog.catalog.collectAsStateWithLifecycle()
    val tabFocus = remember { FocusRequester() }
    val profileId by app.user.profileId.collectAsStateWithLifecycle()
    val profile by produceState<com.fitifiti.tv.data.local.ProfileEntity?>(null, profileId) { value = app.db.profiles().get(profileId) }

    BackHandler(enabled = tab != Tab.Home && LocalScreenActive.current) { tab = Tab.Home; bar.hidden = false; runCatching { tabFocus.requestFocus() } }

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
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Movies -> MediaScreen("movie")
                    Tab.Series -> MediaScreen("series")
                    Tab.Live -> LiveScreen()
                    Tab.Listem -> ListemScreen()
                    Tab.Search -> SearchScreen()
                    Tab.Settings -> SettingsScreen(onProfiles, onEditAccount, onAddAccount)
                }
            }
            AnimatedVisibility(!bar.hidden, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
                TopBar(tab, { if (it != tab) { tab = it; bar.hidden = false } }, profile, onProfiles, tabFocus)
            }
        }
    }
}

@Composable
private fun rememberSaveableTab() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Tab.Home) }

@Composable
private fun TopBar(tab: Tab, onTab: (Tab) -> Unit, profile: com.fitifiti.tv.data.local.ProfileEntity?, onProfiles: () -> Unit, tabFocus: FocusRequester) {
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(C.bg.copy(alpha = 0.85f), Color.Transparent))).padding(horizontal = 48.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(26)
            Spacer(Modifier.width(40.dp))
            listOf(Tab.Home, Tab.Movies, Tab.Series, Tab.Live, Tab.Listem).forEach { t ->
                NavText(t.label, t == tab, { onTab(t) }, if (t == tab) Modifier.focusRequester(tabFocus) else Modifier)
            }
            Spacer(Modifier.weight(1f))
            NavIcon(Icons.Default.Search, "Ara", tab == Tab.Search) { onTab(Tab.Search) }
            NavIcon(Icons.Default.Settings, "Ayarlar", tab == Tab.Settings) { onTab(Tab.Settings) }
            Spacer(Modifier.width(10.dp))
            if (profile != null) Surface(
                onClick = onProfiles, modifier = Modifier.size(38.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            ) { Avatar(profile.name, profile.avatar, 38.dp) }
        }
    }
}

/** Sitedeki sekme: ikonsuz metin; seçili = beyaz + altında mor çizgi, odak = hafif zemin */
@Composable
private fun NavText(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, modifier = modifier.padding(horizontal = 2.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused && !selected) onClick() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = C.fill3, contentColor = if (selected) Color.White else C.muted, focusedContentColor = Color.White),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(22.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) C.primary else Color.Transparent))
        }
    }
}

@Composable
private fun NavIcon(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = Modifier.padding(horizontal = 4.dp).size(44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) C.fill3 else Color.Transparent, focusedContainerColor = Color.White, contentColor = Color.White, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp)) } }
}

/** İlk açılış: gerçek adım + ilerleme çizgisi (sonraki açılışlarda önbellek anında gelir) */
@Composable
private fun LoadingCatalog(step: String, progress: Float) {
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(64)
            Spacer(Modifier.height(36.dp))
            Box(Modifier.width(420.dp)) { ProgressLine(progress, Modifier.fillMaxWidth(), height = 4.dp, track = C.fill3) }
            Spacer(Modifier.height(16.dp))
            Text(step, style = MaterialTheme.typography.bodyLarge, color = C.muted)
            Spacer(Modifier.height(6.dp))
            Text("İlk açılışta katalog indiriliyor; sonra anında açılır.", style = MaterialTheme.typography.bodySmall, color = C.faint)
        }
    }
}
