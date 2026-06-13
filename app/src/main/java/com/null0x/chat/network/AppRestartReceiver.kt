package com.null0x.chat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.null0x.chat.MainActivity

class AppRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_RELAUNCH_APP) return
        val appContext = context.applicationContext
        if (BackgroundConnectionModePreference.currentMode(appContext) != BackgroundConnectionMode.REAL_TIME) {
            return
        }
        if (!consumePendingRelaunch(appContext)) return
        ChatNodeManager.ensureBackgroundNetwork(appContext)
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching {
            appContext.startActivity(launchIntent)
        }
    }

    companion object {
        const val ACTION_RELAUNCH_APP = "com.null0x.chat.action.RELAUNCH_APP"
        private const val PREFS_NAME = "app_restart_guard"
        private const val PENDING_KEY = "pending_relaunch"
        private const val LAST_SCHEDULED_AT_KEY = "last_scheduled_at"
        private const val MIN_RELAUNCH_INTERVAL_MS = 15_000L

        fun markPendingRelaunch(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastScheduledAt = prefs.getLong(LAST_SCHEDULED_AT_KEY, 0L)
            val pending = prefs.getBoolean(PENDING_KEY, false)
            if (pending && now - lastScheduledAt < MIN_RELAUNCH_INTERVAL_MS) {
                return false
            }
            prefs.edit()
                .putBoolean(PENDING_KEY, true)
                .putLong(LAST_SCHEDULED_AT_KEY, now)
                .apply()
            return true
        }

        fun clearPendingRelaunch(context: Context) {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PENDING_KEY, false)
                .apply()
        }

        private fun consumePendingRelaunch(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PENDING_KEY, false)) return false
            prefs.edit()
                .putBoolean(PENDING_KEY, false)
                .apply()
            return true
        }
    }
}
