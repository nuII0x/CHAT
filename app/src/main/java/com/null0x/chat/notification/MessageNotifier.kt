package com.null0x.chat.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.null0x.chat.MainActivity
import com.null0x.chat.R

class MessageNotifier(private val context: Context) {

    companion object {
        const val EXTRA_OPEN_CHAT_USERNAME = "extra_open_chat_username"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    private val channelId = "chat_messages"
    private val groupedMessages = mutableMapOf<String, MutableList<Pair<String, Long>>>()

    init {
        createChannel()
    }

    fun showMessage(fromUsername: String, fromName: String, text: String) {
        val notificationId = fromUsername.hashCode()
        val now = System.currentTimeMillis()
        val list = groupedMessages.getOrPut(fromUsername) { mutableListOf() }
        list.add(text to now)
        if (list.size > 20) {
            list.removeAt(0)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHAT_USERNAME, fromUsername)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Responder")
            .build()
        val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
            putExtra(EXTRA_OPEN_CHAT_USERNAME, fromUsername)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            0,
            "Responder",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val person = Person.Builder().setName(fromName).build()
        val style = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(fromName)
        list.forEach { (msg, ts) ->
            style.addMessage(msg, ts, person)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(fromName)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setGroup("chat_messages_group")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            channelId,
            "Mensagens PrimoChat",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificacoes de novas mensagens"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
