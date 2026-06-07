package com.null0x.chat.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    BLUE,
    LIGHT,
    DARK,
    PINK
}

object ThemePreference {
    private const val PREFS_NAME = "ui_theme"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val LEGACY_DARK_THEME_KEY = "dark_theme_enabled"
    private const val BLUE_DEFAULT_MIGRATED_KEY = "blue_default_migrated"

    private val _themeMode = MutableStateFlow(ThemeMode.BLUE)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedMode = prefs.getString(THEME_MODE_KEY, null)
        val storedThemeMode = storedMode
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        val shouldMigrateOldSystemDefault = storedThemeMode == ThemeMode.SYSTEM &&
            !prefs.getBoolean(BLUE_DEFAULT_MIGRATED_KEY, false) &&
            !prefs.contains(LEGACY_DARK_THEME_KEY)
        val resolvedMode = if (shouldMigrateOldSystemDefault) {
            ThemeMode.BLUE
        } else {
            storedThemeMode
                ?: if (prefs.contains(LEGACY_DARK_THEME_KEY)) {
                    if (prefs.getBoolean(LEGACY_DARK_THEME_KEY, false)) {
                        ThemeMode.DARK
                    } else {
                        ThemeMode.LIGHT
                    }
                } else {
                    ThemeMode.BLUE
                }
        }

        _themeMode.value = resolvedMode
        if (storedMode == null || shouldMigrateOldSystemDefault) {
            prefs.edit()
                .putString(THEME_MODE_KEY, resolvedMode.name)
                .putBoolean(BLUE_DEFAULT_MIGRATED_KEY, true)
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
}
