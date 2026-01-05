package com.example.collage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandPink,
    onSecondary = Color.White,
    tertiary = BrandPurple,
    onTertiary = Color.Black,
    background = Surface0,
    onBackground = Color.White,
    surface = Surface1,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Stroke
)

@Composable
fun SnapNestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography, content = content)
}

@Composable
fun BrandGradient(): Brush = Brush.linearGradient(listOf(BrandPurple, BrandPink, BrandBlue))
