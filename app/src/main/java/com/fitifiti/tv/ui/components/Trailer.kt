package com.fitifiti.tv.ui.components

import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fitifiti.tv.App
import com.fitifiti.tv.data.diag.Diag
import com.fitifiti.tv.ui.LocalScreenActive
import com.fitifiti.tv.ui.theme.C
import kotlinx.coroutines.delay

/** Detay sayfasında oynatılacak fragman: ad + yıl ile aranır, sağlayıcının youtube_trailer alanı varsa önce o */
data class TrailerSpec(val kind: String, val title: String, val year: String?, val provider: String?)

/**
 * Detay hero'sunun sağında fragman (sitedeki TrailerBackdrop): sayfa açıldıktan ~1,2 sn sonra aranır, VPS'teki MP4 hazırsa
 * oynar (hazır değilse sunucu indirir; 90 sn boyunca 5 sn'de bir sorulur). İlk kare gelince sahne görselinin üstüne belirir,
 * ses yavaşça açılır. Sayfa aşağı kaydırılınca durur, oynatıcı açılınca (ekran pasif) serbest bırakılır, bitince görsele döner.
 * SurfaceView saydamlık animasyonu almadığı için TextureView.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.TrailerVideo(spec: TrailerSpec, visible: Boolean) {
    val app = App.instance
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    if (!settings.trailerAutoplay) return
    val active = LocalScreenActive.current
    var url by remember(spec.kind, spec.title) { mutableStateOf<String?>(null) }
    LaunchedEffect(spec) {
        if (url != null) return@LaunchedEffect
        delay(1200)
        val id = app.trailers.find(spec.kind, spec.title, spec.year, spec.provider)
        Diag.log("fragman: \"${spec.title}\" → ${id ?: "yok"}")
        if (id == null) return@LaunchedEffect
        repeat(18) {
            if (app.trailers.isReady(id)) { Diag.log("fragman hazır: $id"); url = app.trailers.fileUrl(id); return@LaunchedEffect }
            delay(5000)
        }
        Diag.log("fragman 90 sn'de hazırlanmadı: $id")
    }
    val u = url ?: return
    var ended by remember(u) { mutableStateOf(false) }
    var pos by remember(u) { mutableLongStateOf(0L) }
    var shown by remember(u) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (shown && visible && active && !ended) 1f else 0f, tween(800), label = "trailer")

    if (active && !ended) {
        val ctx = LocalContext.current
        val player = remember(u) {
            ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(app.http))).build().apply {
                setMediaItem(MediaItem.fromUri(u)); volume = 0f; repeatMode = Player.REPEAT_MODE_OFF
                if (pos > 0) seekTo(pos)
                prepare()
            }
        }
        DisposableEffect(player) {
            val l = object : Player.Listener {
                override fun onRenderedFirstFrame() { shown = true; Diag.log("fragman oynuyor") }
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended = true }
                override fun onPlayerError(error: PlaybackException) {
                    Diag.log("fragman hatası: ${error.errorCodeName} ${error.cause?.message ?: ""}")
                    Diag.send("player", "fragman oynatılamadı: $u\n${error.errorCodeName}\n${error.cause}")
                    ended = true
                }
            }
            player.addListener(l)
            onDispose { pos = player.currentPosition; shown = false; player.removeListener(l); player.release() }
        }
        LaunchedEffect(player, visible) { player.playWhenReady = visible }
        LaunchedEffect(player, shown, settings.trailerSound) {
            if (shown && settings.trailerSound) { for (i in 1..15) { player.volume = 0.6f * i / 15; delay(100) } } else player.volume = 0f
        }
        Box(Modifier.fillMaxWidth(0.78f).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer { this.alpha = alpha }.background(C.bg)) {
            AndroidView(
                factory = { c -> TextureView(c).apply { isFocusable = false; player.setVideoTextureView(this) } },
                update = { player.setVideoTextureView(it) },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            // videonun alt kenarı zemine karışsın (altında görsel yok, düz zemin)
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.35f).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, C.bg))))
            }
        }
    }
}
