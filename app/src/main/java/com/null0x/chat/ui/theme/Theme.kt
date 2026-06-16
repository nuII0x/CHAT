package com.null0x.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AppOnSurfaceDark,
    onPrimary = AppBackgroundDark,
    primaryContainer = AppPrimaryContainerDark,
    onPrimaryContainer = AppOnSurfaceDark,
    secondary = AppOnSurfaceDark,
    onSecondary = AppBackgroundDark,
    secondaryContainer = AppSecondaryContainerDark,
    onSecondaryContainer = AppOnSurfaceDark,
    tertiary = AppOnSurfaceDark,
    onTertiary = AppBackgroundDark,
    tertiaryContainer = AppTertiaryContainerDark,
    onTertiaryContainer = AppOnSurfaceDark,
    background = AppBackgroundDark,
    onBackground = AppOnSurfaceDark,
    surface = AppSurfaceDark,
    onSurface = AppOnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = AppOnSurfaceVariantDark,
    outline = AppOutlineDark,
    outlineVariant = AppSurfaceVariantDark,
    inverseSurface = AppSurfaceLight,
    inverseOnSurface = AppOnSurfaceLight,
    inversePrimary = AppOnSurfaceLight,
    surfaceTint = AppOnSurfaceDark,
    error = AppOnSurfaceDark,
    onError = AppBackgroundDark,
    errorContainer = AppSurfaceVariantDark,
    onErrorContainer = AppOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = AppOnSurfaceLight,
    onPrimary = AppSurfaceLight,
    primaryContainer = AppPrimaryContainerLight,
    onPrimaryContainer = AppOnSurfaceLight,
    secondary = AppOnSurfaceLight,
    onSecondary = AppSurfaceLight,
    secondaryContainer = AppSecondaryContainerLight,
    onSecondaryContainer = AppOnSurfaceLight,
    tertiary = AppOnSurfaceLight,
    onTertiary = AppSurfaceLight,
    tertiaryContainer = AppTertiaryContainerLight,
    onTertiaryContainer = AppOnSurfaceLight,
    background = AppBackgroundLight,
    onBackground = AppOnSurfaceLight,
    surface = AppSurfaceLight,
    onSurface = AppOnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = AppOnSurfaceVariantLight,
    outline = AppOutlineLight,
    outlineVariant = AppSurfaceVariantLight,
    inverseSurface = AppOnSurfaceLight,
    inverseOnSurface = AppSurfaceLight,
    inversePrimary = AppBackgroundDark,
    surfaceTint = AppOnSurfaceLight,
    error = AppOnSurfaceLight,
    onError = AppSurfaceLight,
    errorContainer = AppSurfaceVariantLight,
    onErrorContainer = AppOnSurfaceLight
)

private val PinkColorScheme = lightColorScheme(
    primary = AppPinkPrimary,
    onPrimary = Color.White,
    primaryContainer = AppPinkPrimaryContainer,
    onPrimaryContainer = AppPinkOnSurface,
    secondary = AppPinkSecondary,
    onSecondary = Color.White,
    secondaryContainer = AppPinkSecondaryContainer,
    onSecondaryContainer = AppPinkOnSurface,
    tertiary = AppPinkTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AppPinkTertiaryContainer,
    onTertiaryContainer = AppPinkOnSurface,
    background = AppPinkBackground,
    onBackground = AppPinkOnSurface,
    surface = AppPinkSurface,
    onSurface = AppPinkOnSurface,
    surfaceVariant = AppPinkSurfaceVariant,
    onSurfaceVariant = AppPinkOnSurfaceVariant,
    outline = AppPinkOutline,
    outlineVariant = AppPinkSurfaceVariant,
    inverseSurface = AppPinkOnSurface,
    inverseOnSurface = AppPinkSurface,
    inversePrimary = AppPinkPrimaryContainer,
    surfaceTint = AppPinkPrimary,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val BlueColorScheme = lightColorScheme(
    primary = AppBluePrimary,
    onPrimary = Color.White,
    primaryContainer = AppBluePrimaryContainer,
    onPrimaryContainer = AppBlueOnSurface,
    secondary = AppBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = AppBlueSecondaryContainer,
    onSecondaryContainer = AppBlueOnSurface,
    tertiary = AppBlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = AppBlueTertiaryContainer,
    onTertiaryContainer = AppBlueOnSurface,
    background = AppBlueBackground,
    onBackground = AppBlueOnSurface,
    surface = AppBlueSurface,
    onSurface = AppBlueOnSurface,
    surfaceVariant = AppBlueSurfaceVariant,
    onSurfaceVariant = AppBlueOnSurfaceVariant,
    outline = AppBlueOutline,
    outlineVariant = AppBlueSurfaceVariant,
    inverseSurface = AppBlueOnSurface,
    inverseOnSurface = AppBlueSurface,
    inversePrimary = AppBluePrimaryContainer,
    surfaceTint = AppBluePrimary,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

fun themeBackgroundColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppBackgroundDark else Color.White
        ThemeMode.BLUE -> Color.White
        ThemeMode.LIGHT -> Color.White
        ThemeMode.DARK -> AppBackgroundDark
        ThemeMode.PINK -> Color.White
    }
}

@Composable
fun ChatTheme(
    themeMode: ThemeMode = ThemeMode.BLUE,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) DarkColorScheme else LightColorScheme
        ThemeMode.BLUE -> BlueColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        ThemeMode.PINK -> PinkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ChatShapes,
        typography = Typography,
        content = content
    )
}
