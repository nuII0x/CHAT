package com.null0x.chat.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (BackgroundConnectionModePreference.currentMode(applicationContext) != BackgroundConnectionMode.PERIODIC_SYNC) {
            return Result.success()
        }

        if (!ChatNodeManager.ensureBackgroundNetwork(applicationContext, startSyncLoop = false)) {
            return Result.success()
        }

        return runCatching {
            ChatNodeManager.runBackgroundSyncCycle()
            ChatNodeManager.stop(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
