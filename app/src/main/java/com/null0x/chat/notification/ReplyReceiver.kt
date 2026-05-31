package com.null0x.chat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.null0x.chat.network.ChatNodeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val username = intent.getStringExtra(MessageNotifier.EXTRA_OPEN_CHAT_USERNAME).orEmpty()
        if (username.isBlank()) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessageNotifier.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (replyText.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            ChatNodeManager.sendMessage(username, replyText)
        }
    }
}
