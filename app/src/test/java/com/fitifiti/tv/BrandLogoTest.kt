package com.fitifiti.tv

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.fitifiti.tv.ui.components.BRAND_GLYPH_STYLE
import com.fitifiti.tv.ui.components.drawBrandLogo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Kedili logo animasyonu çizilirken çökmemeli; birkaç anın görüntüsü build/brand-frames altına yazılır (gözle kontrol için) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BrandLogoTest {
    @Test fun framesRender() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val m = TextMeasurer(createFontFamilyResolver(ctx), Density(1f), LayoutDirection.Ltr)
        val f = m.measure("f", BRAND_GLYPH_STYLE, density = Density(1f, 1f))
        val t = m.measure("t", BRAND_GLYPH_STYLE, density = Density(1f, 1f))
        val times = listOf(0.1f, 0.6f, 1.0f, 1.45f, 1.7f, 2.1f, 2.6f, 3.0f, 3.5f, 3.9f, 4.6f, 9.0f)
        val w = 440f; val h = 210f
        val bmp = Bitmap.createBitmap((w * 3).toInt(), (h * 4).toInt(), Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF050508.toInt()) }
        val sheet = android.graphics.Canvas(bmp)
        times.forEachIndexed { i, time ->
            val frame = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(frame.asImageBitmap()), Size(w, h)) { 
                drawBrandLogo(time, f, t, 70f, 0f, 440f, 210f, false, emptyList()) 
            }
            sheet.drawBitmap(frame, (i % 3) * w, (i / 3) * h, null)
        }
        val out = File("build/brand-frames").apply { mkdirs() }
        File(out, "frames.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun compactLiveFramesRender() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val m = TextMeasurer(createFontFamilyResolver(ctx), Density(1f), LayoutDirection.Ltr)
        val f = m.measure("f", BRAND_GLYPH_STYLE, density = Density(1f, 1f))
        val t = m.measure("t", BRAND_GLYPH_STYLE, density = Density(1f, 1f))
        val times = listOf(0.1f, 0.6f, 1.0f, 1.45f, 1.7f, 2.1f, 2.6f, 3.0f, 3.5f, 3.9f, 4.6f, 9.0f)
        val w = 400f; val h = 198f
        val bmp = Bitmap.createBitmap((w * 3).toInt(), (h * 4).toInt(), Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF050508.toInt()) }
        val sheet = android.graphics.Canvas(bmp)
        times.forEachIndexed { i, time ->
            val frame = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(frame.asImageBitmap()), Size(w, h)) { 
                drawBrandLogo(time, f, t, 86f, 4f, w, h, live = true, liveColors = listOf(androidx.compose.ui.graphics.Color(0xFFF472B6), androidx.compose.ui.graphics.Color(0xFFC084FC), androidx.compose.ui.graphics.Color(0xFFFB7185))) 
            }
            sheet.drawBitmap(frame, (i % 3) * w, (i / 3) * h, null)
        }
        val out = File("build/brand-frames").apply { mkdirs() }
        File(out, "frames_compact_live.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
