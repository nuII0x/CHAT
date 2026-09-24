package com.null0x.chat.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AccentColor(val label: String, val color: Color) {
    PURPLE("Roxo", Color(0xFF6D4CB5)),
    BLUE("Azul", Color(0xFF1565C0)),
    CYAN("Ciano", Color(0xFF00838F)),
    GREEN("Verde", Color(0xFF2E7D32)),
    ORANGE("Laranja", Color(0xFFEF6C00)),
    RED("Vermelho", Color(0xFFC62828)),
    PINK("Rosa", Color(0xFFAD1457));
}

data class AppearanceSettings(
    val tabs: ThemeMode = ThemeMode.SYSTEM,
    val dialogs: ThemeMode = ThemeMode.SYSTEM,
    val conversations: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.PURPLE
)

object AppearancePreference {
    private const val PREFS_NAME = "ui_appearance"
    private const val ACCENT_COLOR_KEY = "accent_color"

    private val _appearance = MutableStateFlow(AppearanceSettings())
    val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accent = runCatching {
            AccentColor.valueOf(prefs.getString(ACCENT_COLOR_KEY, null).orEmpty())
        }.getOrDefault(AccentColor.PURPLE)
        val baseTheme = ThemePreference.themeMode.value
        _appearance.value = AppearanceSettings(
            tabs = baseTheme,
            dialogs = baseTheme,
            conversations = baseTheme,
            accentColor = accent
        )
        prefs.edit()
            .remove("contextual_bars")
            .remove("tabs")
            .remove("dialogs")
            .remove("conversations")
            .putString(ACCENT_COLOR_KEY, accent.name)
            .apply()
    }

    fun setAccentColor(context: Context, accentColor: AccentColor) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(ACCENT_COLOR_KEY, accentColor.name)
            .apply()
        _appearance.value = _appearance.value.copy(accentColor = accentColor)
    }

    internal fun syncThemeMode(themeMode: ThemeMode) {
        _appearance.value = _appearance.value.copy(
            tabs = themeMode,
            dialogs = themeMode,
            conversations = themeMode
        )
    }
}
