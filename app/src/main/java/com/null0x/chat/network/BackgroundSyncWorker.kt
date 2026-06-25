package com.null0x.chat.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackgroundSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!ChatNodeManager.ensureBackgroundNetwork(applicationContext, startSyncLoop = false)) {
            return Result.success()
        }

        return runCatching {
            ChatNodeManager.runBackgroundSyncCycle()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
