package com.fitifiti.tv

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.fitifiti.tv.ui.components.BRAND_GLYPH_STYLE
import com.fitifiti.tv.ui.components.drawBrandCat
import com.fitifiti.tv.ui.components.drawBrandLogo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Uygulama simgesi ve Android TV ana ekran banner'ı, uygulamanın kendi logo çiziminden (kedi + "fıtıfıtı") üretilir:
 *   ./gradlew testDebugUnitTest --tests '*BrandAssetsTest*'  → app/build/brand-assets/
 * Kedi son karede (oturmuş, gözler açık; t = 9 sn). Çıktılar res/ altına elle kopyalanır (bkz. CLAUDE.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BrandAssetsTest {
    private val out = File("build/brand-assets").apply { mkdirs() }
    private val bg = Color(0xFF050508)
    private val violet = Color(0xFF8B5CF6)
    private val teal = Color(0xFF2DD4BF)

    private fun png(name: String, w: Int, h: Int, draw: DrawScope.() -> Unit) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp.asImageBitmap()), Size(w.toFloat(), h.toFloat()), draw)
        File(out, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Obsidian zemin + sitedeki gibi çok hafif mor (sol üst) ve turkuaz (sağ alt) ışıma */
    private fun DrawScope.backdrop() {
        drawRect(bg)
        drawRect(Brush.radialGradient(listOf(violet.copy(alpha = 0.30f), Color.Transparent), center = Offset(size.width * 0.18f, size.height * 0.1f), radius = size.maxDimension * 0.7f))
        drawRect(Brush.radialGradient(listOf(teal.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 1.0f), radius = size.maxDimension * 0.6f))
    }

    /** Kediyi (140×140 çerçeve) verilen kutuya ortalar */
    private fun DrawScope.cat(cx: Float, cy: Float, px: Float) {
        val k = px / 140f
        translate(cx - px / 2, cy - px / 2) { scale(k, k, Offset.Zero) { drawBrandCat(9f) } }
    }

    @Test fun render() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val m = TextMeasurer(createFontFamilyResolver(ctx), Density(1f), LayoutDirection.Ltr)
        val f = m.measure("f", BRAND_GLYPH_STYLE, density = Density(1f, 1f))
        val t = m.measure("t", BRAND_GLYPH_STYLE, density = Density(1f, 1f))

        // Banner (Android TV ana ekranı): 320×180 dp → xhdpi 640×360. Logo (kedi son "ı"da oturmuş) ortada.
        png("app_banner.png", 640, 360) {
            backdrop()
            val lw = 600f; val lh = lw * 210f / 440f
            // görünür içerik (kedinin başı → harflerin altı) sahnede y≈95…180 → onun ortası banner'ın ortasına
            translate((size.width - lw) / 2 - 8f, size.height / 2 - 137f * (lw / 440f)) {
                // drawBrandLogo çizim alanının genişliğine ölçeklenir → alt alan boyutunda ayrı tuvale çiz
                val sub = Bitmap.createBitmap(lw.toInt(), lh.toInt(), Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(sub.asImageBitmap()), Size(lw, lh)) {
                    drawBrandLogo(9f, f, t, 70f, 0f, 440f, 210f, false, emptyList())
                }
                drawImage(sub.asImageBitmap())
            }
        }

        // Uyarlanabilir simge (Android 8+): 108 dp → xxxhdpi 432 px. Ön plan yalnız kedi, güvenli alan ortadaki 66 dp.
        png("ic_launcher_foreground.png", 432, 432) { cat(216f, 222f, 250f) }
        png("ic_launcher_background.png", 432, 432) { backdrop() }

        // Eski cihazlar için hazır simge (yuvarlak köşeli koyu kare + kedi), her yoğunlukta
        listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192).forEach { (d, px) ->
            png("ic_launcher-$d.png", px, px) {
                val r = px * 0.22f
                drawRoundRect(bg, size = size, cornerRadius = CornerRadius(r))
                drawRoundRect(Brush.radialGradient(listOf(violet.copy(alpha = 0.32f), Color.Transparent), center = Offset(px * 0.2f, px * 0.1f), radius = px * 0.9f), size = size, cornerRadius = CornerRadius(r))
                cat(px / 2f, px * 0.5f, px * 0.78f)
            }
        }
    }
}
