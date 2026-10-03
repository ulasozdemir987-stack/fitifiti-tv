package com.fitifiti.tv.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.tv.material3.Typography
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.fitifiti.tv.R

@OptIn(ExperimentalTextApi::class)
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

@OptIn(ExperimentalTextApi::class)
val ManropeFont = FontFamily(
    Font(googleFont = GoogleFont("Manrope"), fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = GoogleFont("Manrope"), fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = GoogleFont("Manrope"), fontProvider = provider, weight = FontWeight.Normal)
)

@OptIn(ExperimentalTextApi::class)
val InterFont = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.SemiBold)
)

@OptIn(ExperimentalTvMaterial3Api::class)
val AppTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.ExtraBold),
    displayMedium = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.ExtraBold),
    displaySmall = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    titleSmall = androidx.compose.ui.text.TextStyle(fontFamily = ManropeFont, fontWeight = FontWeight.Bold),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal),
    bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Normal),
    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium),
    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium),
    labelSmall = androidx.compose.ui.text.TextStyle(fontFamily = InterFont, fontWeight = FontWeight.Medium)
)
