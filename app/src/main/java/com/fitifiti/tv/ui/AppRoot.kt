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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import com.fitifiti.tv.data.diag.Diag
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    // Telefondaki "ana sayfa" tuşu
    LaunchedEffect(Unit) { com.fitifiti.tv.data.remote.RemoteBus.home.collect { while (nav.stack.size > 1 && nav.top != Route.Main) nav.back() } }
    var crash by remember { mutableStateOf(runCatching { app.crashFile().takeIf { it.exists() }?.readText() }.getOrNull()) }

    // Tüm listelerde odak kaydırması kısa ve keskin (varsayılan yay kumandada gecikmeli hissettiriyordu); ekranlar kendi
    // konumlandırmalarını içeride yine verebilir
    val density = androidx.compose.ui.platform.LocalDensity.current
    val snappy = remember(density) {
        val pad = with(density) { 24.dp.toPx() }
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override val scrollAnimationSpec = com.fitifiti.tv.ui.screens.SnappyScroll
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                size >= containerSize -> offset
                offset < pad -> offset - pad
                offset + size > containerSize - pad -> offset + size - (containerSize - pad)
                else -> 0f
            }
        }
    }
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    CompositionLocalProvider(LocalActions provides actions, androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides snappy) {
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
                            Route.Main -> MainScreen(onProfiles = { app.user.profileId.value = -1; nav.reset(Route.Profiles) }, onEditAccount = { nav.push(Route.AddAccount(it)) }, onAddAccount = { nav.push(Route.AddAccount()) }, onEditChannels = { nav.push(Route.ChannelEdit) })
                            is Route.MovieDetail -> MovieDetailScreen(r.movie)
                            is Route.SeriesDetail -> SeriesDetailScreen(r.series, r.focusEpisodeId)
                            is Route.CategoryPage -> CategoryScreen(r.kind, r.categoryId, r.genre)
                            is Route.Platform -> PlatformScreen(r.brand)
                            is Route.Player -> PlayerScreen(r.req, onClose = { nav.back() })
                            is Route.LivePlayer -> LivePlayerScreen(r.channelId, onClose = { nav.back() })
                            Route.ChannelEdit -> ChannelEditScreen(onBack = { nav.back() })
                            Route.LiveCalendar -> LiveCalendarScreen(onBack = { nav.back() })
                        }
                    }
                }
            }
            val fb by com.fitifiti.tv.data.diag.Feedback.pending.collectAsState()
            fb?.let { c -> com.fitifiti.tv.ui.components.FeedbackDialog(c) { com.fitifiti.tv.data.diag.Feedback.pending.value = null } }
            val update by com.fitifiti.tv.data.update.Updater.prompt.collectAsState()
            update?.let { info -> if (crash == null) com.fitifiti.tv.ui.components.UpdateDialog(info) { com.fitifiti.tv.data.update.Updater.dismiss() } }
            crash?.let { text -> CrashReport(text) { runCatching { app.crashFile().delete(); java.io.File(app.filesDir, "last-crash.sent").delete() }; crash = null } }
            
            val reminder by com.fitifiti.tv.domain.ReminderManager.currentReminder.collectAsState()
            reminder?.let { rem ->
                Box(Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { kotlinx.coroutines.delay(100); runCatching { focus.requestFocus() } }
                    androidx.tv.material3.Surface(
                        onClick = { 
                            com.fitifiti.tv.domain.ReminderManager.currentReminder.value = null
                            nav.push(Route.LivePlayer(rem.channelId))
                        },
                        modifier = Modifier.focusRequester(focus),
                        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = com.fitifiti.tv.ui.theme.C.primary)
                    ) {
                        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val chName = app.catalog.catalog.value.channelById[rem.channelId]?.let { com.fitifiti.tv.domain.cleanChannelName(it.name) } ?: "kanal"
                            Text("${rem.title} başlıyor · $chName kanalına geç?", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
            androidx.tv.material3.Text("Hata raporu geliştiriciye otomatik gönderildi. İnternet yoksa bu ekranın fotoğrafını çekip gönderebilirsin.", color = C.muted)
            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
            androidx.tv.material3.Text(text.lines().filter { it.isNotBlank() }.take(28).joinToString("\n"), color = androidx.compose.ui.graphics.Color(0xCCFFFFFF),
                fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1f))
            com.fitifiti.tv.ui.components.Btn("Tamam", onClose, Modifier.focusRequester(f))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ScreenLayer(active: Boolean, content: @Composable () -> Unit) {
    val fr = remember { FocusRequester() }
    val mem = remember { FocusMemory() }
    var wasInactive by remember { mutableStateOf(false) }
    var layerHasFocus by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    if (active) LaunchedEffect(Unit) {
        var attempt = 0
        FocusRescue.requests.collect { force ->
            val state = "katmanda odak=$layerHasFocus · hafızada odak=${mem.current != null} · son=${mem.last != null}"
            // Tek işlenmeyen tuş listenin kenarı olabilir; öğe odaktaysa dokunma. Üst üste ise odak takılmıştır
            // (ör. yerleşmemiş bir katmandaki öğede kalmış): katmanda "odak var" görünse de zorla kurtar.
            if (!force && (layerHasFocus || mem.current != null)) { Diag.log("kurtarma yok (tek tuş) · $state"); return@collect }
            if (force) attempt++ else attempt = 0
            val stuck = force && mem.current != null // öğe odakta ama hiçbir yöne geçilemiyor: o öğeye geri dönmek işe yaramaz
            if (force) runCatching { focusManager.clearFocus(force = true) }
            var how = "hiçbiri"
            val fb = mem.fallback
            // Takılmışsa önce sol menü (her zaman yerleşik ve görünür); denemeler sürerse hedefler sırayla değişir.
            if (stuck && fb != null && attempt % 2 == 1 && runCatching { fb.requestFocus(); true }.getOrDefault(false)) how = "menü"
            if (how == "hiçbiri" && (!stuck || attempt % 2 == 0) && mem.restore()) how = "son öğe"
            if (how == "hiçbiri" && fb != null && runCatching { fb.requestFocus(); true }.getOrDefault(false)) how = "menü"
            if (how == "hiçbiri") { runCatching { fr.requestFocus() }; how = "ilk öğe" }
            Diag.log("odak kurtarıldı ($how${if (force) ", zorla #$attempt" else ""}) · önce: $state")
        }
    }
    LaunchedEffect(active) {
        if (!active) wasInactive = true
        else if (wasInactive) {
            // Önce tam olarak en son odaklanan kart; yerleşir yerleşmez (her karede bir deneme, en fazla ~10 kare).
            // Odaksız geçen her an tehlikeli: o sırada basılan yön tuşu odağı sol üstteki sekmeye atıyordu.
            var ok = false
            if (mem.last != null) for (i in 0 until 10) {
                androidx.compose.runtime.withFrameNanos { }
                ok = mem.restore()
                if (ok || mem.last == null) break
            }
            if (!ok) runCatching { fr.requestFocus() }
        }
    }
    Box(
        Modifier.fillMaxSize()
            .layout { m, c -> val p = m.measure(c); layout(p.width, p.height) { if (active) p.place(0, 0) } }
            .focusProperties { canFocus = active }
            .onFocusChanged { layerHasFocus = it.hasFocus }
            .focusRequester(fr)
            .focusGroup(),
    ) {
        CompositionLocalProvider(LocalScreenActive provides active, LocalFocusMemory provides mem) { content() }
    }
}
