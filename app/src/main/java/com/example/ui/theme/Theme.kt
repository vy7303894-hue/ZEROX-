package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF380824),
    onPrimaryContainer = NeonMagentaLight,
    secondary = NeonCyan,
    onSecondary = Color(0xFF002026),
    secondaryContainer = Color(0xFF0A2B35),
    onSecondaryContainer = NeonCyanLight,
    tertiary = ElectricViolet,
    onTertiary = Color.White,
    background = CyberObsidian,
    onBackground = TextPrimary,
    surface = CyberDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberCardSurface,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderGlow
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

