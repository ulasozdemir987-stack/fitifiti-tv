package com.fitifiti.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.layout.layout
import com.fitifiti.tv.App
import com.fitifiti.tv.data.local.ProfileEntity
import com.fitifiti.tv.ui.player.LivePlayerScreen
import com.fitifiti.tv.ui.player.PlayerScreen
import com.fitifiti.tv.ui.screens.*
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay

/** Ekranı açık olan (en üstteki) mi — alttakiler çizilmez ve odak almaz ama durumlarını (kaydırma, odak) korur */
val LocalScreenActive = compositionLocalOf { true }

@Composable
fun AppRoot() {
    val app = App.instance
    val nav = remember { Navigator(if (app.accounts.accounts.value.isEmpty()) Route.Login else Route.Profiles) }
    val actions = remember { Actions(nav) }
    val accounts by app.accounts.accounts.collectAsState()

    fun selectProfile(p: ProfileEntity) {
        val acc = app.accounts.active ?: return
        app.user.profileId.value = p.id
        app.settings.update { it.copy(lastProfileId = p.id) }
        app.catalog.start(acc)
        nav.reset(Route.Main)
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    var crash by remember { mutableStateOf(runCatching { app.crashFile().takeIf { it.exists() }?.readText() }.getOrNull()) }

    CompositionLocalProvider(LocalActions provides actions) {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            val stack = nav.stack.toList()
            stack.forEachIndexed { i, entry ->
                key(entry.id) {
                    val active = i == stack.lastIndex
                    // Oynatıcının altındaki ekranlar da yerinde kalır; yalnız en üstteki yerleşir (çizilir + odak alır)
                    ScreenLayer(active) {
                        when (val r = entry.route) {
                            Route.Login -> LoginScreen(null, onDone = { nav.reset(Route.Profiles) }, onCancel = null)
                            is Route.AddAccount -> LoginScreen(r.editId, onDone = { app.catalog.clear(); nav.reset(Route.Profiles) }, onCancel = { nav.back() })
                            Route.Profiles -> if (accounts.isEmpty()) LoginScreen(null, onDone = { nav.reset(Route.Profiles) }, onCancel = null) else ProfilesScreen(::selectProfile, onAddAccount = { nav.push(Route.AddAccount()) }, onEditAccount = { nav.push(Route.AddAccount(it)) })
                            Route.Main -> MainScreen(onProfiles = { app.user.profileId.value = -1; nav.reset(Route.Profiles) }, onEditAccount = { nav.push(Route.AddAccount(it)) }, onAddAccount = { nav.push(Route.AddAccount()) })
                            is Route.MovieDetail -> MovieDetailScreen(r.movie)
                            is Route.SeriesDetail -> SeriesDetailScreen(r.series, r.focusEpisodeId)
                            is Route.CategoryPage -> CategoryScreen(r.kind, r.categoryId, r.genre)
                            is Route.Player -> PlayerScreen(r.req, onClose = { nav.back() })
                            is Route.LivePlayer -> LivePlayerScreen(r.channelId, r.list, onClose = { nav.back() })
                        }
                    }
                }
            }
            crash?.let { text -> CrashReport(text) { runCatching { app.crashFile().delete() }; crash = null } }
        }
    }
}

/** Önceki açılışta çöktüyse hatanın özeti (fotoğrafı çekilip geliştiriciye gönderilsin diye) */
@Composable
private fun CrashReport(text: String, onClose: () -> Unit) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { f.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().padding(40.dp).background(C.panel, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(28.dp),
        ) {
            androidx.tv.material3.Text("Uygulama son açılışta kapandı", style = androidx.tv.material3.MaterialTheme.typography.headlineSmall)
            androidx.tv.material3.Text("Bu ekranın fotoğrafını çekip gönderirsen sorunu bulup düzeltebiliriz.", color = C.muted)
            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
            androidx.tv.material3.Text(text.lines().filter { it.isNotBlank() }.take(28).joinToString("\n"), color = androidx.compose.ui.graphics.Color(0xCCFFFFFF),
                fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
            com.fitifiti.tv.ui.components.Btn("Tamam", onClose, Modifier.focusRequester(f))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ScreenLayer(active: Boolean, content: @Composable () -> Unit) {
    val fr = remember { FocusRequester() }
    var wasInactive by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) wasInactive = true
        else if (wasInactive) { delay(30); runCatching { fr.restoreFocusedChild() || run { fr.requestFocus(); true } } }
    }
    Box(
        Modifier.fillMaxSize()
            .layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { if (active) p.place(0, 0) } }
            .focusProperties { canFocus = active }
            .focusRequester(fr)
            .focusRestorer()
            .focusGroup(),
    ) {
        CompositionLocalProvider(LocalScreenActive provides active) { content() }
    }
}
