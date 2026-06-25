package com.null0x.chat.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppearanceSection {
    TABS,
    DIALOGS,
    CONVERSATIONS
}

data class AppearanceSettings(
    val tabs: ThemeMode = ThemeMode.BLUE,
    val dialogs: ThemeMode = ThemeMode.BLUE,
    val conversations: ThemeMode = ThemeMode.BLUE
)

object AppearancePreference {
    private const val PREFS_NAME = "ui_appearance"
    private const val TABS_KEY = "tabs"
    private const val DIALOGS_KEY = "dialogs"
    private const val CONVERSATIONS_KEY = "conversations"

    private val _appearance = MutableStateFlow(AppearanceSettings())
    val appearance: StateFlow<AppearanceSettings> = _appearance.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val base = ThemePreference.themeMode.value
        prefs.edit().remove("contextual_bars").apply()
        val resolved = AppearanceSettings(
            tabs = base,
            dialogs = base,
            conversations = readMode(prefs, CONVERSATIONS_KEY, base)
        )
        _appearance.value = resolved
        if (prefs.all.isEmpty()) {
            prefs.edit()
                .putString(CONVERSATIONS_KEY, resolved.conversations.name)
                .apply()
        }
    }

    fun setSectionMode(context: Context, section: AppearanceSection, mode: ThemeMode) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(sectionKey(section), mode.name)
            .apply()
        _appearance.value = _appearance.value.withSection(section, mode)
    }

    private fun readMode(prefs: android.content.SharedPreferences, key: String, fallback: ThemeMode): ThemeMode {
        return prefs.getString(key, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: fallback
    }

    private fun sectionKey(section: AppearanceSection): String {
        return when (section) {
            AppearanceSection.TABS -> TABS_KEY
            AppearanceSection.DIALOGS -> DIALOGS_KEY
            AppearanceSection.CONVERSATIONS -> CONVERSATIONS_KEY
        }
    }

    private fun AppearanceSettings.withSection(section: AppearanceSection, mode: ThemeMode): AppearanceSettings {
        return when (section) {
            AppearanceSection.TABS -> copy(tabs = mode)
            AppearanceSection.DIALOGS -> copy(dialogs = ThemePreference.themeMode.value)
            AppearanceSection.CONVERSATIONS -> copy(conversations = mode)
        }
    }
}
