package com.fitifiti.tv.ui.components

import android.view.TextureView
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.Icons
import androidx.media3.common.VideoSize
import android.os.Looper
import android.os.HandlerThread
import android.os.Handler
import android.content.Context
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

/** Detay sayfasındaki fragmanın durumu: başlıktaki "Sesi kapat" düğmesi bunu okur/yazar */
class TrailerState { var playing by mutableStateOf(false); var muted by mutableStateOf(false) }
val LocalTrailerState = staticCompositionLocalOf<TrailerState?> { null }

/** Başlık bloğundaki ses düğmesi: yalnız fragman oynarken görünür; seçim ayara yazılır (sonraki fragmanlar da öyle başlar) */
@Composable
fun TrailerMuteButton(modifier: Modifier = Modifier) {
    val st = LocalTrailerState.current ?: return
    if (!st.playing) return
    Btn(if (st.muted) "Sesi aç" else "Sesi kapat", {
        st.muted = !st.muted
        App.instance.settings.update { it.copy(trailerSound = !st.muted) }
    }, modifier, kind = BtnKind.Ghost, icon = if (st.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp)
}

/**
 * Detay hero'sunun sağında fragman (sitedeki TrailerBackdrop): sayfa açıldıktan ~1,2 sn sonra aranır, VPS'teki MP4 hazırsa
 * oynar (hazır değilse sunucu indirir; 90 sn boyunca 5 sn'de bir sorulur). İlk kare gelince sahne görselinin yerine belirir,
 * ses yavaşça açılır. Sayfa aşağı kaydırılınca durur, oynatıcı açılınca (ekran pasif) serbest bırakılır, bitince görsele döner.
 *
 * - Oynatıcı AYRI bir iş parçacığında çalışır: release() ana iş parçacığında çözücüyü kapatırken TV'yi yüzlerce ms
 *   kilitliyordu (geri tuşuna basınca sayfa geç kapanıyor, tuşlar "algılanmıyor" gibiydi).
 * - Görüntü bölgeyi KAPLAR (kırparak): eskiden 16:9 kutu üstte durup altı/üstü düz siyah kalıyordu.
 * - SurfaceView saydamlık animasyonu almadığı için TextureView.
 */
@OptIn(UnstableApi::class)
@Composable
fun BoxScope.TrailerVideo(spec: TrailerSpec, visible: Boolean) {
    val app = App.instance
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val st = LocalTrailerState.current
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
    var videoW by remember(u) { mutableIntStateOf(0) }
    var videoH by remember(u) { mutableIntStateOf(0) }
    val alpha by animateFloatAsState(if (shown && visible && active && !ended) 1f else 0f, tween(800), label = "trailer")
    LaunchedEffect(st, shown, ended, active) { st?.playing = shown && !ended && active }
    LaunchedEffect(st) { st?.muted = !settings.trailerSound }
    val muted = st?.muted ?: !settings.trailerSound

    if (active && !ended) {
        val ctx = LocalContext.current
        val engine = remember(u) { TrailerEngine(ctx, app.http, u, pos) }
        DisposableEffect(engine) {
            engine.listener = object : Player.Listener {
                override fun onRenderedFirstFrame() { shown = true; Diag.log("fragman oynuyor") }
                override fun onVideoSizeChanged(size: VideoSize) { if (size.width > 0) { videoW = (size.width * size.pixelWidthHeightRatio).toInt(); videoH = size.height } }
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) ended = true }
                override fun onPlayerError(error: PlaybackException) {
                    Diag.log("fragman hatası: ${error.errorCodeName} ${error.cause?.message ?: ""}")
                    Diag.send("player", "fragman oynatılamadı: $u\n${error.errorCodeName}\n${error.cause}")
                    ended = true
                }
            }
            onDispose { pos = engine.position; shown = false; engine.release() }
        }
        LaunchedEffect(engine, visible) { engine.post { playWhenReady = visible } }
        LaunchedEffect(engine, shown, muted) {
            if (shown && !muted) { for (i in 1..15) { val v = 0.6f * i / 15; engine.post { volume = v }; delay(100) } } else engine.post { volume = 0f }
        }
        Box(Modifier.fillMaxWidth(0.78f).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer { this.alpha = alpha }.background(C.bg)) {
            AndroidView(
                factory = { c -> TextureView(c).apply {
                    isFocusable = false
                    engine.post { setVideoTextureView(this@apply) }
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v.tag as? Pair<*, *>)?.let { (w, h) -> coverCrop(v as TextureView, w as Int, h as Int) } }
                } },
                update = { it.tag = videoW to videoH; coverCrop(it, videoW, videoH) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Görüntüyü kutuyu kaplayacak biçimde büyütür (taşan kenarlar kırpılır); TextureView varsayılanda gerer */
private fun coverCrop(v: TextureView, vw: Int, vh: Int) {
    val w = v.width.toFloat(); val h = v.height.toFloat()
    if (w <= 0f || h <= 0f || vw <= 0 || vh <= 0) return
    val scale = maxOf(w / vw, h / vh)
    val m = android.graphics.Matrix()
    m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f)
    v.setTransform(m)
}

/** ExoPlayer'ı kendi iş parçacığında (Looper) çalıştırır; tüm çağrılar o iş parçacığına gönderilir */
@OptIn(UnstableApi::class)
private class TrailerEngine(ctx: Context, http: okhttp3.OkHttpClient, url: String, startMs: Long) {
    private val thread = HandlerThread("trailer").apply { start() }
    private val handler = Handler(thread.looper)
    private var player: ExoPlayer? = null
    @Volatile var position = startMs; private set
    @Volatile var listener: Player.Listener? = null
    private val relay = object : Player.Listener {
        private fun ui(f: (Player.Listener) -> Unit) { val l = listener ?: return; Handler(Looper.getMainLooper()).post { f(l) } }
        override fun onRenderedFirstFrame() = ui { it.onRenderedFirstFrame() }
        override fun onVideoSizeChanged(videoSize: VideoSize) = ui { it.onVideoSizeChanged(videoSize) }
        override fun onPlaybackStateChanged(playbackState: Int) = ui { it.onPlaybackStateChanged(playbackState) }
        override fun onPlayerError(error: PlaybackException) = ui { it.onPlayerError(error) }
    }

    init {
        val app = ctx.applicationContext
        handler.post {
            player = ExoPlayer.Builder(app).setLooper(thread.looper)
                .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http))).build().apply {
                    addListener(relay)
                    setMediaItem(MediaItem.fromUri(url)); volume = 0f; repeatMode = Player.REPEAT_MODE_OFF
                    if (startMs > 0) seekTo(startMs)
                    prepare()
                }
        }
    }

    fun post(f: ExoPlayer.() -> Unit) { handler.post { player?.let { runCatching { it.f() } } } }

    fun release() {
        listener = null
        handler.post {
            player?.let { position = it.currentPosition; it.removeListener(relay); it.release() }
            player = null
            thread.quitSafely()
        }
    }
}
