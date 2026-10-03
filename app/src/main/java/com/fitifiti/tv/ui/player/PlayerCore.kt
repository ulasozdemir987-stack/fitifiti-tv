package com.fitifiti.tv.ui.player

import android.content.Context
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.fitifiti.tv.App
import com.fitifiti.tv.data.xtream.BROWSER_UA
import java.util.Locale

/**
 * Ortak ExoPlayer kurulumu: sağlayıcıya tarayıcı gibi bağlanan OkHttp kaynağı, cihazın çözemediği seslerde
 * (AC3/E-AC3/DTS…) ffmpeg yazılım çözücüsü, IPTV'ye uygun tampon.
 */
@OptIn(UnstableApi::class)
fun buildPlayer(ctx: Context, live: Boolean): ExoPlayer {
    val app = App.instance
    val ds = OkHttpDataSource.Factory(app.http).setUserAgent(BROWSER_UA)
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
        .setConstantBitrateSeekingEnabled(true)
    val renderers = DefaultRenderersFactory(ctx)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)
    // Tampon bayt sınırı: varsayılan (~140 MB, Java belleğinde) düşük bellekli TV'lerde 4K'da uygulamayı düşürebiliyor
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(if (live) 8_000 else 15_000, if (live) 30_000 else 50_000, 2_000, 4_000)
        .setTargetBufferBytes(48 * 1024 * 1024)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()
    val prefs = app.settings.value
    return ExoPlayer.Builder(ctx, renderers)
        .setMediaSourceFactory(DefaultMediaSourceFactory(ds, extractors))
        .setLoadControl(load)
        .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
        .build().apply {
            addAnalyticsListener(DiagListener)
            trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                if (prefs.subtitleLang == "off") setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true)
                else setPreferredTextLanguages(prefs.subtitleLang, if (prefs.subtitleLang == "tr") "tur" else "eng")
                setPreferredAudioLanguages("tr", "tur")
            }.build()
        }
}

/** Oynatıcı olayları tanılama halkasına: hangi çözücü, hangi biçim, düşen kareler, hatalar */
@OptIn(UnstableApi::class)
private object DiagListener : androidx.media3.exoplayer.analytics.AnalyticsListener {
    private val D = com.fitifiti.tv.data.diag.Diag
    override fun onVideoDecoderInitialized(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) = D.log("görüntü çözücü: $decoderName (${initializationDurationMs} ms)")
    override fun onAudioDecoderInitialized(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) = D.log("ses çözücü: $decoderName")
    override fun onVideoInputFormatChanged(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, f: Format, d: androidx.media3.exoplayer.DecoderReuseEvaluation?) = D.log("görüntü: ${f.sampleMimeType} ${f.codecs ?: ""} ${f.width}x${f.height} ${f.frameRate}fps ${f.bitrate / 1000}kbps")
    override fun onAudioInputFormatChanged(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, f: Format, d: androidx.media3.exoplayer.DecoderReuseEvaluation?) = D.log("ses: ${f.sampleMimeType} ${f.channelCount}ch ${f.language ?: ""}")
    override fun onDroppedVideoFrames(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) = D.log("düşen kare: $droppedFrames / ${elapsedMs} ms · ${D.memory()}")
    override fun onPlayerError(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, error: androidx.media3.common.PlaybackException) {
        D.log("HATA ${error.errorCodeName}: ${error.cause?.javaClass?.simpleName}: ${error.cause?.message ?: error.message}")
        D.send("player", "${error.errorCodeName}\n${error.stackTraceToString().take(4000)}\n\nSon olaylar:\n${D.snapshot()}")
    }
    override fun onPlaybackStateChanged(e: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, state: Int) {
        if (state == Player.STATE_BUFFERING || state == Player.STATE_READY) D.log("durum: ${if (state == Player.STATE_READY) "hazır" else "yükleniyor"} · ${D.memory()}")
    }
}

fun mediaItem(url: String, live: Boolean = false): MediaItem {
    com.fitifiti.tv.data.diag.Diag.log("aç: ${url.substringAfterLast('/')} ${if (live) "(canlı)" else ""}")
    val b = MediaItem.Builder().setUri(url)
    if (url.substringBefore('?').endsWith(".m3u8", true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    if (live) b.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(6_000).build())
    return b.build()
}

/** Görüntü + altyazı katmanı (kontroller Compose ile, PlayerView'un kendi kontrolleri kapalı) */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(player: ExoPlayer, modifier: Modifier, subtitleScale: Float = 1f) {
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                keepScreenOn = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AColor.BLACK)
                isFocusable = false
                isFocusableInTouchMode = false
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                this.player = player
                subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setStyle(CaptionStyleCompat(AColor.WHITE, AColor.TRANSPARENT, AColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AColor.BLACK, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)))
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f * subtitleScale)
                    setBottomPaddingFraction(0.07f)
                }
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}

// --- ses / altyazı parçaları ---

data class TrackOption(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

private val TR = Locale("tr", "TR")
private fun langName(code: String?): String? {
    if (code.isNullOrBlank() || code == "und") return null
    val l = Locale.forLanguageTag(code).getDisplayLanguage(TR)
    return l.takeIf { it.isNotBlank() && it != code }?.replaceFirstChar { it.titlecase(TR) } ?: code.uppercase()
}

@OptIn(UnstableApi::class)
private fun trackLabel(f: Format, type: Int, i: Int): String {
    val lang = langName(f.language)
    val extra = buildList {
        if (type == MC.TRACK_TYPE_AUDIO) {
            when {
                f.channelCount >= 6 -> add("5.1")
                f.channelCount == 2 -> add("Stereo")
            }
            when (f.sampleMimeType) { MimeTypes.AUDIO_AC3 -> add("Dolby"); MimeTypes.AUDIO_E_AC3 -> add("Dolby+"); MimeTypes.AUDIO_DTS -> add("DTS") }
        }
        if ((f.selectionFlags and MC.SELECTION_FLAG_FORCED) != 0) add("zorunlu")
    }
    val name = f.label?.takeIf { it.isNotBlank() && !it.equals(f.language, true) }
    return listOfNotNull(lang ?: name ?: "${if (type == MC.TRACK_TYPE_AUDIO) "Ses" else "Altyazı"} ${i + 1}", if (lang != null) name else null).distinct().joinToString(" · ") +
        if (extra.isNotEmpty()) " (${extra.joinToString(", ")})" else ""
}

@OptIn(UnstableApi::class)
fun tracksOf(player: Player, type: Int): List<TrackOption> {
    val out = mutableListOf<TrackOption>()
    var n = 0
    for (g in player.currentTracks.groups) {
        if (g.type != type) continue
        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i, true)) continue
            out += TrackOption(trackLabel(g.getTrackFormat(i), type, n++), g, i, g.isTrackSelected(i))
        }
    }
    return out
}

fun selectTrack(player: Player, type: Int, opt: TrackOption?) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
        if (opt?.group == null) setTrackTypeDisabled(type, true)
        else {
            setTrackTypeDisabled(type, false)
            setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
        }
    }.build()
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
