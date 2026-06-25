package com.null0x.chat.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.null0x.chat.AppBranding
import com.null0x.chat.MainActivity
import com.null0x.chat.R

class AppNetworkService : Service() {

    companion object {
        private val NOTIFICATION_CHANNEL_ID = AppBranding.internalId("network_live_v2")
        private const val NOTIFICATION_ID = 4207
        private const val RELAUNCH_REQUEST_CODE = 5202
        @Volatile
        private var suppressRelaunch = false

        fun start(context: Context) {
            val appContext = context.applicationContext
            if (!BackgroundConnectionModeController.isRealTimeMode(appContext)) return
            suppressRelaunch = false
            AppRestartReceiver.clearPendingRelaunch(appContext)
            val intent = Intent(appContext, AppNetworkService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            suppressRelaunch = true
            appContext.stopService(Intent(appContext, AppNetworkService::class.java))
        }

        internal fun shouldSuppressRelaunch(): Boolean = suppressRelaunch
    }

    override fun onCreate() {
        super.onCreate()
        if (!BackgroundConnectionModeController.isRealTimeMode(applicationContext)) {
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!BackgroundConnectionModeController.isRealTimeMode(applicationContext)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!shouldSuppressRelaunch() && BackgroundConnectionModeController.isRealTimeMode(applicationContext)) {
            scheduleRelaunch(applicationContext)
            ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (!shouldSuppressRelaunch() && BackgroundConnectionModeController.isRealTimeMode(applicationContext)) {
            scheduleRelaunch(applicationContext)
        }
        super.onDestroy()
    }

    private fun buildNotification() = NotificationCompat.Builder(applicationContext, ensureChannel())
        .setSmallIcon(R.drawable.ic_stat_nochat)
        .setContentTitle(AppBranding.APP_NAME)
        .setContentText("Conexão em segundo plano")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                applicationContext,
                NOTIFICATION_ID,
                Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return NOTIFICATION_CHANNEL_ID
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Conexão ${AppBranding.APP_NAME}",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Mantém a conexão em tempo real em segundo plano"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        return NOTIFICATION_CHANNEL_ID
    }

    private fun scheduleRelaunch(context: Context) {
        if (shouldSuppressRelaunch()) {
            AppRestartReceiver.clearPendingRelaunch(context)
            return
        }
        val appContext = context.applicationContext
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
