package com.null0x.chat.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppearanceSettings(
    val tabs: ThemeMode = ThemeMode.SYSTEM,
    val dialogs: ThemeMode = ThemeMode.SYSTEM,
    val conversations: ThemeMode = ThemeMode.SYSTEM
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
        prefs.edit()
            .remove("contextual_bars")
            .remove(TABS_KEY)
            .remove(DIALOGS_KEY)
            .remove(CONVERSATIONS_KEY)
            .apply()
        val resolved = AppearanceSettings(
            tabs = base,
            dialogs = base,
            conversations = base
        )
        _appearance.value = resolved
    }
}
