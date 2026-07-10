package com.null0x.chat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.null0x.chat.network.ChatNodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MessageNotifier.ACTION_REPLY) return
        val route = intent.getStringExtra(MessageNotifier.EXTRA_REPLY_USERNAME)
            ?.trim()
            .orEmpty()
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessageNotifier.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (route.isBlank() || reply.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (ChatNodeManager.ensureBackgroundNetwork(context.applicationContext)) {
                    val messageId = UUID.randomUUID().toString()
                    val result = ChatNodeManager.sendChatMessage(route, reply, messageId)
                    if (result.isSuccess) {
                        MessageNotifier(context.applicationContext).cancelMessage(route)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
