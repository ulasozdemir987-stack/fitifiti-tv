package com.fitifiti.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalTvMaterial3Api::class)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B5CF6), // Purple accent
    background = Color(0xFF050508), // Dark theme ground
    surface = Color(0xFF12121C), // Panel color
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A26),
    onSurfaceVariant = Color.White
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FitifitiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
