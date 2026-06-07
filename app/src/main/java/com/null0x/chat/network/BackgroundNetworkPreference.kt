package com.null0x.chat.network

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BackgroundNetworkPreference {
    private const val PREFS_NAME = "background_network"
    private const val ENABLED_KEY = "enabled"

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(ENABLED_KEY, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED_KEY, enabled)
            .apply()
        _enabled.value = enabled
        if (enabled) {
            AppNetworkService.start(appContext)
            NetworkBootstrapScheduler.schedule(appContext)
        } else {
            BackgroundRelaunchPreference.setEnabled(appContext, false)
            AppRestartReceiver.clearPendingRelaunch(appContext)
            appContext.stopService(Intent(appContext, AppNetworkService::class.java))
        }
    }

    fun isEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ENABLED_KEY, true)
    }
}
