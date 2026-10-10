package com.fitifiti.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.fitifiti.tv.R

// Sitenin renkleri (app/globals.css): obsidian zemin, mor yalnız "seçili" ve "asıl eylem" için
object C {
    val bg = Color(0xFF050508)
    val panel = Color(0xFF12121C)
    val fill1 = Color(0x0AFFFFFF) // %4
    val fill2 = Color(0x12FFFFFF) // %7
    val fill3 = Color(0x1AFFFFFF) // %10
    val line = Color(0x17FFFFFF)  // %9
    val primary = Color(0xFF8B5CF6)
    val teal = Color(0xFF2DD4BF)
    val cyan = Color(0xFF22D3EE)
    val text = Color.White
    val muted = Color(0x8CFFFFFF) // %55
    val faint = Color(0x66FFFFFF) // %40
    val danger = Color(0xFFF87171)
    val progress = Brush.horizontalGradient(listOf(primary, teal))
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
)

/** Büyük başlıklar: Manrope ExtraBold, sıkı harf aralığı (sitedeki font-display) */
val Display = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, color = Color.White)

/** Tüm uygulama Manrope (eskiden gövde yazıları sistem fontuydu, başlıklarla uyumsuzdu) */
private val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, letterSpacing = 0.005.em)

private val typography = Typography(
    displayLarge = Display.copy(fontSize = 42.sp, lineHeight = 46.sp),
    displayMedium = Display.copy(fontSize = 40.sp, lineHeight = 44.sp),
    displaySmall = Display.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = Body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em),
    titleLarge = Body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = Body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Body.copy(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = Body.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = Body.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = Body.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

/** Ekranın gerçek yüksekliği / genişliği (dp, arayüz ölçeği uygulanmış) — LocalConfiguration ölçeği bilmez */
@Composable
fun screenHeightDp(): Float {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    val d = androidx.compose.ui.platform.LocalDensity.current
    return c.screenHeightDp * (c.densityDpi / 160f) / d.density
}

/**
 * Arayüz ölçeği: tüm uygulama (yazılar, kartlar, boşluklar) tek oranla küçülür (Ayarlar → Arayüz boyutu). OwnTV ile
 * piksel karşılaştırmasında bizim öğeler ~1,35 kat büyüktü; varsayılan 0,75 ile aynı ferahlık.
 */
@Composable
fun FitifitiTheme(content: @Composable () -> Unit) {
    val scale = com.fitifiti.tv.App.instance.settings.settings.collectAsState().value.uiScale
    val base = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(base.density * scale, base.fontScale)) {
        ThemeInner(content)
    }
}

@Composable
private fun ThemeInner(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.primary, onPrimary = Color.White, background = C.bg, onBackground = Color.White,
            surface = C.panel, onSurface = Color.White, surfaceVariant = C.fill2, onSurfaceVariant = Color.White,
            border = C.line,
        ),
        typography = typography,
    ) {
        // tv-material'da Surface dışındaki Text'in varsayılan rengi SİYAH (LocalContentColor = Black) → bölüm adları vb. siyah görünüyordu
        androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color.White) {
            // stil verilmeyen Text'ler de Manrope olsun
            androidx.tv.material3.ProvideTextStyle(typography.bodyMedium) { content() }
        }
    }
}
