package com.null0x.chat.network

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundConnectionMode(
    val label: String,
    val description: String
) {
    REAL_TIME(
        label = "Sempre conectado",
        description = "Mantém a conexão ativa para receber e enviar mensagens mais rápido."
    )
}

object BackgroundConnectionModePreference {
    private val _mode = MutableStateFlow(BackgroundConnectionMode.REAL_TIME)
    val mode: StateFlow<BackgroundConnectionMode> = _mode.asStateFlow()

    fun initialize(context: Context) {
        _mode.value = BackgroundConnectionMode.REAL_TIME
    }

    fun setMode(context: Context, mode: BackgroundConnectionMode) {
        _mode.value = BackgroundConnectionMode.REAL_TIME
    }

    fun currentMode(context: Context): BackgroundConnectionMode {
        return BackgroundConnectionMode.REAL_TIME
    }
}

object BackgroundConnectionModeController {
    fun initialize(context: Context) {
        BackgroundConnectionModePreference.initialize(context)
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }

    fun setMode(context: Context, mode: BackgroundConnectionMode) {
        BackgroundConnectionModePreference.setMode(context, mode)
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }

    fun onAppVisible(context: Context) {
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }

    fun onAppHidden(context: Context) {
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }

    fun schedulePeriodicSync(context: Context) {
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }

    fun cancelPeriodicSync(context: Context) {
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }

    fun isRealTimeMode(context: Context): Boolean {
        return true
    }

    internal fun apply(context: Context, visible: Boolean) {
        AppNetworkService.start(context.applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)
    }
}
