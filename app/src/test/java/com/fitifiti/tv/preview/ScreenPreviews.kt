package com.fitifiti.tv.preview

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.fitifiti.tv.App
import com.fitifiti.tv.ui.*
import com.fitifiti.tv.ui.player.*
import com.fitifiti.tv.ui.screens.*
import com.fitifiti.tv.ui.theme.FitifitiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Ekran önizlemeleri: gerçek ekranlar örnek veriyle 1920×1080 TV ekranında çizilip `app/build/screens/` altına PNG
 * olarak yazılır (kutuya kurmadan tasarımı görmek için). Çalıştır:
 *   ./gradlew testDebugUnitTest --tests '*ScreenPreviews*'          (hepsi)
 *   ./gradlew testDebugUnitTest --tests '*ScreenPreviews.detail*'   (tek ekran)
 * `keys`: çizimden önce basılacak kumanda tuşları (odak durumlarını görmek için).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi", sdk = [34])
@OptIn(ExperimentalTestApi::class)
class ScreenPreviews {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, keys: List<Key> = emptyList(), settle: Long = 2500, content: @Composable () -> Unit) {
        val app = App.instance
        Shadows.shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        PreviewData.install(app)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            FitifitiTheme { CompositionLocalProvider(LocalActions provides Actions(Navigator(Route.Main))) { content() } }
        }
        settle(settle)
        keys.forEach { k -> rule.onRoot().performKeyInput { pressKey(k) }; settle(500) }
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        v.draw(canvas)
        // açık pencereler (Dialog) ayrı pencerede: ekrandaki yerlerine, arkası karartılarak çizilir
        windowRoots().filter { it !== v && it.isShown }.forEach { w ->
            canvas.drawColor(0x99000000.toInt())
            val loc = IntArray(2); w.getLocationOnScreen(loc)
            val x = if (loc[0] == 0 && w.width < v.width) (v.width - w.width) / 2f else loc[0].toFloat()
            val y = if (loc[1] == 0 && w.height < v.height) (v.height - w.height) / 2f else loc[1].toFloat()
            canvas.save(); canvas.translate(x, y); w.draw(canvas); canvas.restore()
        }
        val small = Bitmap.createScaledBitmap(bmp, 960, 540, true)
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowRoots(): List<android.view.View> = runCatching {
        val c = Class.forName("android.view.WindowManagerGlobal")
        val g = c.getMethod("getInstance").invoke(null)
        val f = c.getDeclaredField("mViews").apply { isAccessible = true }
        (f.get(g) as List<android.view.View>).toList()
    }.getOrDefault(emptyList())

    /** Saati ilerletir; arka planda (görsel, Room) biten işlerin ana iş parçacığına dönmesini bekler */
    private fun settle(ms: Long) {
        var left = ms
        while (left > 0) {
            rule.mainClock.advanceTimeBy(100); left -= 100
            Thread.sleep(15)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    private val movie get() = PreviewData.movies[1]
    private val series get() = PreviewData.series[0]
    private fun noop() {}

    @Test fun home() = shoot("home") { MainScreen({}, {}, {}) }
    @Test fun homeRows() = shoot("home-rows", listOf(Key.DirectionDown, Key.DirectionDown, Key.DirectionRight)) { MainScreen({}, {}, {}) }
    @Test fun movies() = shoot("movies") { MainScreen({}, {}, {}, startTab = Tab.Movies) }
    @Test fun live() = shoot("live", listOf(Key.DirectionDown, Key.DirectionDown)) { MainScreen({}, {}, {}, startTab = Tab.Live) }
    @Test fun guide() = shoot("guide") { MainScreen({}, {}, {}, startTab = Tab.Guide) }
    @Test fun search() = shoot("search") { MainScreen({}, {}, {}, startTab = Tab.Search) }
    @Test fun listem() = shoot("listem") { MainScreen({}, {}, {}, startTab = Tab.Listem) }
    @Test fun settings() = shoot("settings") { MainScreen({}, {}, {}, startTab = Tab.Settings) }
    @Test fun detail() = shoot("detail-movie") { MovieDetailScreen(movie) }
    @Test fun detailIcon() = shoot("detail-movie-icon", listOf(Key.DirectionRight)) { MovieDetailScreen(movie) }
    @Test fun detailSeries() = shoot("detail-series") { SeriesDetailScreen(series, null) }
    @Test fun detailSeriesEpisodes() = shoot("detail-series-episodes", listOf(Key.DirectionDown)) { SeriesDetailScreen(series, null) }

    private fun req(episode: Boolean) = if (episode) {
        val e = PreviewData.episodes(series)[1]!![2]
        PlayRequest("episode", "fake://video", "episode-${e.id}", e.id, series.name, "1. Sezon · 3. Bölüm", "fake:backdrop:s0", "mkv", 25 * 60_000, true, series = series, episode = e)
    } else PlayRequest("movie", "fake://video", "movie-${movie.id}", "${movie.id}", movie.name, null, movie.icon, "mp4", 6 * 60_000, true, movie = movie)

    private fun meta(episode: Boolean) = PlayerMeta(
        logo = if (episode) "fake:logo:${series.name}" else null, backdrop = "fake:backdrop:m1", overview = movie.plot, vote = 7.8, votes = 1500,
        year = "2024", runtime = "2 sa 7 dk", genres = "Dram", cast = PreviewData.cast, epName = if (episode) "Bölüm adı 3" else null, epOverview = if (episode) "Bölüm özeti burada." else null,
    )
    @Test fun playerLoading() = shoot("player-loading", settle = 3000) { PlayerLoading(req(false), meta(false)) }
    @Test fun playerPause() = shoot("player-pause") { PauseScreen(true, req(true), meta(true), compact = false) }
    @Test fun playerResume() = shoot("player-resume") { ResumePrompt(req(false), meta(false), ::noop, ::noop) }
    @Test fun feedback() = shoot("feedback") {
        val bmp = android.graphics.Bitmap.createBitmap(960, 540, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2A2140.toInt()) }
        Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            MainScreen({}, {}, {})
            com.fitifiti.tv.ui.components.FeedbackDialog(com.fitifiti.tv.data.diag.Feedback.Capture(null, bmp, "", "Ana ekran"), {})
        }
    }
}
