package com.fitifiti.tv.ui.theme

import androidx.compose.runtime.Composable
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
)

/** Büyük başlıklar: Manrope ExtraBold, sıkı harf aralığı (sitedeki font-display) */
val Display = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em, color = Color.White)

private val typography = Typography(
    displayLarge = Display.copy(fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = Display.copy(fontSize = 40.sp, lineHeight = 44.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun FitifitiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.primary, onPrimary = Color.White, background = C.bg, onBackground = Color.White,
            surface = C.panel, onSurface = Color.White, surfaceVariant = C.fill2, onSurfaceVariant = Color.White,
            border = C.line,
        ),
        typography = typography,
    ) {
        // tv-material'da Surface dışındaki Text'in varsayılan rengi SİYAH (LocalContentColor = Black) → bölüm adları vb. siyah görünüyordu
        androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color.White) { content() }
    }
}
