package com.null0x.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
