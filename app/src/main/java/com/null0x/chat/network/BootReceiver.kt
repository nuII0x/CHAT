package com.null0x.chat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                when (BackgroundConnectionModePreference.currentMode(context)) {
                    BackgroundConnectionMode.REAL_TIME -> AppNetworkService.start(context)
                    BackgroundConnectionMode.PERIODIC_SYNC -> BackgroundSyncScheduler.schedule(context)
                }
            }
        }
    }
}
