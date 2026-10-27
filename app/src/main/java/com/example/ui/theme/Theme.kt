package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

private val DarkColorScheme = darkColorScheme(
    primary = AiAccentGreen,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = AiAccentGreenLight,
    secondary = DarkTextSecondary,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkTextPrimary,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = BorderGlow
)

private val LightColorScheme = lightColorScheme(
    primary = AiAccentGreen,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = AiAccentGreen,
    secondary = LightTextSecondary,
    onSecondary = LightBg,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFD1D1D6)
)

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
