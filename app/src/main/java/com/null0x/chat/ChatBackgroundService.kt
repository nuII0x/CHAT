package com.null0x.chat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.null0x.chat.network.ChatNodeManager

class ChatBackgroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        ChatNodeManager.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ChatNodeManager.start(applicationContext)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startHidden(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                appContext.startService(Intent(appContext, ChatBackgroundService::class.java))
            }.onFailure {
                ChatNodeManager.start(appContext)
            }
        }
    }
}
