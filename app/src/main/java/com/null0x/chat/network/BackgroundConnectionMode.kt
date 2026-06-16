package com.null0x.chat.network

import android.content.Context
import com.null0x.chat.AppVisibility
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundConnectionMode(
    val label: String,
    val description: String
) {
    PERIODIC_SYNC(
        label = "De tempos em tempos",
        description = "Mantém o app leve e sincroniza em segundo plano quando necessário."
    ),
    REAL_TIME(
        label = "Sempre conectado",
        description = "Mantém a conexão ativa para receber e enviar mensagens mais rápido."
    )
}

object BackgroundConnectionModePreference {
    private const val PREFS_NAME = "background_connection_mode"
    private const val MODE_KEY = "mode"

    private val _mode = MutableStateFlow(BackgroundConnectionMode.REAL_TIME)
    val mode: StateFlow<BackgroundConnectionMode> = _mode.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(MODE_KEY, BackgroundConnectionMode.REAL_TIME.name).apply()
        _mode.value = BackgroundConnectionMode.REAL_TIME
        BackgroundRelaunchPreference.setEnabled(context.applicationContext, true)
    }

    fun setMode(context: Context, mode: BackgroundConnectionMode) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(MODE_KEY, BackgroundConnectionMode.REAL_TIME.name)
            .apply()
        _mode.value = BackgroundConnectionMode.REAL_TIME
        BackgroundRelaunchPreference.setEnabled(appContext, true)
        BackgroundConnectionModeController.apply(appContext, AppVisibility.isVisible)
    }

    fun currentMode(context: Context): BackgroundConnectionMode {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(MODE_KEY, BackgroundConnectionMode.REAL_TIME.name)
            .apply()
        return BackgroundConnectionMode.REAL_TIME
    }
}

object BackgroundConnectionModeController {
    fun initialize(context: Context) {
        BackgroundConnectionModePreference.initialize(context)
    }

    fun setMode(context: Context, mode: BackgroundConnectionMode) {
        BackgroundConnectionModePreference.setMode(context, mode)
    }

    fun onAppVisible(context: Context) {
        apply(context.applicationContext, true)
    }

    fun onAppHidden(context: Context) {
        apply(context.applicationContext, false)
    }

    fun schedulePeriodicSync(context: Context) {
        BackgroundSyncScheduler.schedule(context.applicationContext)
    }

    fun cancelPeriodicSync(context: Context) {
        BackgroundSyncScheduler.cancel(context.applicationContext)
    }

    fun isRealTimeMode(context: Context): Boolean {
        return true
    }

    internal fun apply(context: Context, visible: Boolean) {
        val appContext = context.applicationContext
        cancelPeriodicSync(appContext)
        AppNetworkService.start(appContext)
        ChatNodeManager.ensureBackgroundNetwork(appContext)
    }
}

object BackgroundSyncScheduler {
    private const val UNIQUE_WORK_NAME = "nullchat_background_sync"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val request = androidx.work.PeriodicWorkRequestBuilder<BackgroundSyncWorker>(
            15,
            java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
