package com.null0x.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SignalBlue80,
    secondary = FreshGreen80,
    tertiary = WarmCoral80,
    background = AppBackgroundDark,
    surface = AppSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = SignalBlue40,
    secondary = FreshGreen40,
    tertiary = WarmCoral40,
    background = AppBackgroundLight,
    surface = AppSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight
)

@Composable
fun ChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
