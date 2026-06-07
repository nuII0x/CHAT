package com.null0x.chat.network

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AppNetworkService : Service() {

    override fun onCreate() {
        super.onCreate()
        if (!BackgroundNetworkPreference.isEnabled(applicationContext)) {
            stopSelf()
            return
        }
        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BackgroundNetworkPreference.isEnabled(applicationContext)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRelaunch(applicationContext)
        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scheduleRelaunch(applicationContext)
        super.onDestroy()
    }

    companion object {
        private const val RELAUNCH_REQUEST_CODE = 5202

        fun start(context: Context) {
            val appContext = context.applicationContext
            AppRestartReceiver.clearPendingRelaunch(appContext)
            if (!BackgroundNetworkPreference.isEnabled(appContext)) return
            val intent = Intent(appContext, AppNetworkService::class.java)
            try {
                appContext.startService(intent)
            } catch (_: IllegalStateException) {
                ChatNodeManager.ensureBackgroundNetwork(appContext)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    NetworkBootstrapScheduler.schedule(appContext)
                }
            }
        }

        private fun scheduleRelaunch(context: Context) {
            val appContext = context.applicationContext
            if (!BackgroundRelaunchPreference.isEnabled(appContext)) {
                AppRestartReceiver.clearPendingRelaunch(appContext)
                return
            }
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
            if (!AppRestartReceiver.markPendingRelaunch(appContext)) return
            val intent = Intent(appContext, AppRestartReceiver::class.java).apply {
                component = ComponentName(appContext, AppRestartReceiver::class.java)
                action = AppRestartReceiver.ACTION_RELAUNCH_APP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                RELAUNCH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + 750L
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}
