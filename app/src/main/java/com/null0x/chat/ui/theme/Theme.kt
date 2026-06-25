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
        ThemeMode.BLUE -> AppBlueBackground
        ThemeMode.LIGHT -> Color.White
        ThemeMode.DARK -> AppBackgroundDark
        ThemeMode.PINK -> AppPinkBackground
    }
}

fun themeAccentColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppOnSurfaceDark else AppOnSurfaceLight
        ThemeMode.BLUE -> AppBluePrimary
        ThemeMode.LIGHT -> AppOnSurfaceLight
        ThemeMode.DARK -> AppOnSurfaceDark
        ThemeMode.PINK -> AppPinkPrimary
    }
}

fun themeContextualBarColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppSurfaceVariantDark else AppSurfaceVariantLight
        ThemeMode.BLUE -> AppBluePrimary
        ThemeMode.LIGHT -> AppSurfaceLight
        ThemeMode.DARK -> AppSurfaceDark
        ThemeMode.PINK -> AppPinkPrimary
    }
}

fun themeSurfaceTint(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) AppSurfaceVariantDark else AppSurfaceVariantLight
        ThemeMode.BLUE -> AppBlueSurface
        ThemeMode.LIGHT -> AppSurfaceLight
        ThemeMode.DARK -> AppSurfaceDark
        ThemeMode.PINK -> AppPinkSurface
    }
}

fun themeDialogColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return themeSurfaceTint(themeMode, systemDarkTheme)
}

fun themeConversationBubbleColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) Color.Black else Color.White
        ThemeMode.BLUE -> AppBluePrimary
        ThemeMode.LIGHT -> Color.White
        ThemeMode.DARK -> Color.Black
        ThemeMode.PINK -> AppPinkPrimary
    }
}

fun themeConversationPeerBubbleColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.SYSTEM -> Color(0xFFE7E7E7)
        ThemeMode.BLUE -> AppBlueSurfaceVariant
        ThemeMode.LIGHT -> Color(0xFFE7E7E7)
        ThemeMode.DARK -> Color(0xFFE7E7E7)
        ThemeMode.PINK -> AppPinkSurfaceVariant
    }
}

fun themeConversationTextColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return readableContentColor(themeConversationBubbleColor(themeMode, systemDarkTheme))
}

fun themeConversationPeerTextColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return readableContentColor(themeConversationPeerBubbleColor(themeMode, systemDarkTheme))
}

fun dockSelectedBackgroundColor(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.LIGHT -> Color.Transparent
        ThemeMode.SYSTEM -> if (systemDarkTheme) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        }
        ThemeMode.BLUE, ThemeMode.PINK -> Color.Black.copy(alpha = 0.08f)
        ThemeMode.DARK -> Color.White.copy(alpha = 0.16f)
    }
}

fun dockSelectedIconTint(themeMode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (themeMode) {
        ThemeMode.LIGHT -> Color.Black
        ThemeMode.SYSTEM -> if (systemDarkTheme) Color.White else Color.Black
        ThemeMode.BLUE, ThemeMode.PINK -> Color.Black
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
