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
    IconAction(if (st.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (st.muted) "Sesi aç" else "Sesi kapat", {
        st.muted = !st.muted
        App.instance.settings.update { it.copy(trailerSound = !st.muted) }
    }, modifier, showLabel = false)
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
    LaunchedEffect(shown, visible, active, ended) { Diag.log("fragman durum: görünür=$visible gösterildi=$shown etkin=$active bitti=$ended") }
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
        // Fragman dosyasına gömülü sinemaskop bantları (2.39:1 film, 16:9 dosya): görüntü oturunca küçük bir kare alıp
        // üst/alt siyah satırları sayar, kırpmayı ona göre büyütür (yoksa üstte siyah şerit kalıyordu)
        var bars by remember(u) { mutableFloatStateOf(0f) }
        val tvRef = remember(u) { arrayOfNulls<TextureView>(1) }
        LaunchedEffect(shown) {
            if (!shown) return@LaunchedEffect
            for (t in listOf(1500L, 4000L, 9000L)) {
                delay(if (t == 1500L) t else t - 2500L)
                val bmp = runCatching { tvRef[0]?.getBitmap(48, 27) }.getOrNull() ?: continue
                bars = maxOf(bars, letterbox(bmp, videoW, videoH, tvRef[0]!!))
                bmp.recycle()
            }
        }
        Box(Modifier.fillMaxWidth(0.78f).fillMaxHeight().align(Alignment.TopEnd).graphicsLayer { this.alpha = alpha }.background(C.bg)) {
            AndroidView(
                factory = { c -> TextureView(c).apply {
                    isFocusable = false
                    engine.attach(this)
                    tvRef[0] = this
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v.tag as? Triple<*, *, *>)?.let { (w, h, b) -> coverCrop(v as TextureView, w as Int, h as Int, b as Float) } }
                } },
                update = { it.tag = Triple(videoW, videoH, bars); coverCrop(it, videoW, videoH, bars) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Görüntüyü kutuyu kaplayacak biçimde büyütür (taşan kenarlar kırpılır); TextureView varsayılanda gerer.
 * `bars` = üst ve alttaki siyah bandın her biri, kare yüksekliğinin oranı (0..0.2) — bantlar da kutunun dışına itilir.
 */
private fun coverCrop(v: TextureView, vw: Int, vh: Int, bars: Float = 0f) {
    val w = v.width.toFloat(); val h = v.height.toFloat()
    if (w <= 0f || h <= 0f || vw <= 0 || vh <= 0) return
    val content = vh * (1f - 2f * bars.coerceIn(0f, 0.2f))
    val scale = maxOf(w / vw, h / content)
    val m = android.graphics.Matrix()
    m.setScale(vw * scale / w, vh * scale / h, w / 2f, h / 2f)
    v.setTransform(m)
}

/**
 * TextureView'dan alınan küçük karede (görünüm koordinatı, mevcut kırpma uygulanmış) üst/alt siyah satırları sayıp
 * KAYNAK karedeki bant oranını tahmin eder. Görüntü kırpılmış göründüğü için ölçüm kaba; en fazla %20.
 */
private fun letterbox(bmp: android.graphics.Bitmap, vw: Int, vh: Int, v: TextureView): Float {
    fun dark(y: Int): Boolean {
        var sum = 0
        for (x in 0 until bmp.width) { val c = bmp.getPixel(x, y); sum += ((c shr 16 and 255) + (c shr 8 and 255) + (c and 255)) / 3 }
        return sum / bmp.width < 14
    }
    var top = 0; while (top < bmp.height / 3 && dark(top)) top++
    var bot = 0; while (bot < bmp.height / 3 && dark(bmp.height - 1 - bot)) bot++
    val darkRows = minOf(top, bot) // iki tarafta da olmalı (karanlık sahne değil, bant)
    if (darkRows == 0 || vw <= 0 || vh <= 0 || v.width <= 0) return 0f
    // görünümde görünen kaynak yüksekliği oranı: kaplama ölçeğiyle görünüm yüksekliği kaynağın ne kadarını gösteriyor
    val scale = maxOf(v.width.toFloat() / vw, v.height.toFloat() / vh)
    val visibleSrc = v.height / scale / vh // görünen kaynak yüksekliği / kaynak yüksekliği
    val hidden = (1f - visibleSrc) / 2f
    return (hidden + darkRows.toFloat() / bmp.height * visibleSrc + 0.01f).coerceIn(0f, 0.2f)
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

    /**
     * Yüzeyi kendimiz yönetiriz: `setVideoTextureView` kullanılsaydı ExoPlayer TextureView'a dinleyici takar ve görünüm
     * ana iş parçacığında kaldırılırken (sayfadan çıkış) oynatıcının iş parçacığı dışından çağrılıp çökerdi
     * ("ListenerSet.verifyCurrentThread", 2.5.1). SurfaceTexture'ı oynatıcı yüzeyi bıraktıktan SONRA serbest bırakırız.
     */
    fun attach(tv: TextureView) {
        fun use(st: android.graphics.SurfaceTexture) {
            val surface = android.view.Surface(st)
            surfaces[st] = surface
            post { setVideoSurface(surface) }
        }
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) = use(st)
            override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
                val surface = surfaces.remove(st)
                handler.post {
                    if (surface != null) runCatching { player?.clearVideoSurface(surface) }
                    surface?.release()
                    Handler(Looper.getMainLooper()).post { st.release() }
                }
                return false // SurfaceTexture'ı yukarıda, oynatıcı bıraktıktan sonra biz serbest bırakıyoruz
            }
        }
        tv.surfaceTexture?.let { if (tv.isAvailable) use(it) }
    }
    private val surfaces = java.util.concurrent.ConcurrentHashMap<android.graphics.SurfaceTexture, android.view.Surface>()

    fun release() {
        listener = null
        handler.post {
            player?.let { position = it.currentPosition; it.removeListener(relay); it.release() }
            player = null
            thread.quitSafely()
        }
    }
}
