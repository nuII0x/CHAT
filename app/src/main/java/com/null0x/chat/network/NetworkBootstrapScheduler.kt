package com.null0x.chat.network

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NetworkBootstrapScheduler {
    private const val BOOTSTRAP_WORK_NAME = "null0xchat_background_sync"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            BOOTSTRAP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
