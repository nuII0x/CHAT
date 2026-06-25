package com.null0x.chat.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BackgroundNetworkPreference {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun initialize(context: Context) {
        _enabled.value = true
        AppNetworkService.start(context.applicationContext)
        NetworkBootstrapScheduler.schedule(context.applicationContext)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        _enabled.value = true
        AppNetworkService.start(appContext)
        NetworkBootstrapScheduler.schedule(appContext)
    }

    fun isEnabled(context: Context): Boolean {
        return true
    }
}
