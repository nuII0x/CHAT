package com.null0x.chat.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemePreference {
    private const val PREFS_NAME = "ui_theme"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val LEGACY_DARK_THEME_KEY = "dark_theme_enabled"

    private val _themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedMode = prefs.getString(THEME_MODE_KEY, null)
        val storedThemeMode = parseThemeMode(storedMode)
        val resolvedMode = when (storedThemeMode) {
            null -> if (prefs.contains(LEGACY_DARK_THEME_KEY)) {
                if (prefs.getBoolean(LEGACY_DARK_THEME_KEY, false)) {
                    ThemeMode.DARK
                } else {
                    ThemeMode.LIGHT
                }
            } else {
                ThemeMode.LIGHT
            }
            else -> storedThemeMode
        }

        _themeMode.value = resolvedMode
        if (storedMode == null || storedThemeMode == null) {
            prefs.edit()
                .putString(THEME_MODE_KEY, resolvedMode.name)
                .apply()
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME_MODE_KEY, mode.name)
            .apply()
        _themeMode.value = mode
    }

    private fun parseThemeMode(value: String?): ThemeMode? {
        return when (value) {
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> null
        }
    }
}
