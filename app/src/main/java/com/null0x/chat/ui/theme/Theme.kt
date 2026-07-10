package com.null0x.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

fun themeBackgroundColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppBackgroundDark else Color.White
        ThemeMode.LIGHT -> Color.White
        ThemeMode.DARK -> AppBackgroundDark
    }
}

fun themeAccentColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppOnSurfaceDark else AppOnSurfaceLight
        ThemeMode.LIGHT -> AppOnSurfaceLight
        ThemeMode.DARK -> AppOnSurfaceDark
    }
}

fun themeContextualBarColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppSurfaceDark else AppSurfaceLight
        ThemeMode.LIGHT -> AppSurfaceLight
        ThemeMode.DARK -> AppSurfaceDark
    }
}

fun themeSurfaceTint(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppSurfaceDark else AppSurfaceLight
        ThemeMode.LIGHT -> AppSurfaceLight
        ThemeMode.DARK -> AppSurfaceDark
    }
}

fun themeDialogColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return themeSurfaceTint(themeMode, systemDarkTheme)
}

fun dockSelectedBackgroundColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) Color.White.copy(alpha = 0.16f) else Color.Transparent
        ThemeMode.LIGHT -> Color.Transparent
        ThemeMode.DARK -> Color.White.copy(alpha = 0.16f)
    }
}

fun dockSelectedIconTint(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) Color.White else Color.Black
        ThemeMode.LIGHT -> Color.Black
        ThemeMode.DARK -> Color.White
    }
}

fun readableContentColor(background: Color): Color {
    return if (background.luminance() > 0.55f) Color.Black else Color.White
}

fun readableContentColor(background: Color, alpha: Float): Color {
    return readableContentColor(background).copy(alpha = alpha)
}

@Composable
fun ChatTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val systemDarkTheme = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) DarkColorScheme else LightColorScheme
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ChatShapes,
        typography = Typography,
        content = content
    )
}
