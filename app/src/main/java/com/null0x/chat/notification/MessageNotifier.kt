package com.null0x.chat.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.null0x.chat.MainActivity
import com.null0x.chat.R
import java.util.UUID

class MessageNotifier(private val context: Context) {

    companion object {
        private const val ACTION_OPEN_CHAT = "com.null0x.chat.action.OPEN_CHAT"
        private const val EXTRA_OPEN_CHAT_TOKEN = "extra_open_chat_token"
        private const val OPEN_CHAT_PREFS = "notification_open_chat"
        private const val OPEN_CHAT_TOKEN_PREFIX = "token:"
        const val KEY_TEXT_REPLY = "key_text_reply"

        fun consumeOpenChatUsername(context: Context, intent: Intent?): String? {
            if (intent?.action != ACTION_OPEN_CHAT) return null
            val token = intent.getStringExtra(EXTRA_OPEN_CHAT_TOKEN)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val prefs = context.applicationContext.getSharedPreferences(OPEN_CHAT_PREFS, Context.MODE_PRIVATE)
            val key = "$OPEN_CHAT_TOKEN_PREFIX$token"
            val username = prefs.getString(key, null)?.trim().orEmpty()
            prefs.edit().remove(key).apply()
            return username.takeIf { it.isNotBlank() }
        }
    }

    private val channelId = "chat_messages_v2"

    init {
        createChannel()
    }

    fun showMessage(fromUsername: String, fromName: String, text: String) {
        val notificationId = fromUsername.hashCode()
        val token = UUID.randomUUID().toString()
        context.getSharedPreferences(OPEN_CHAT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$OPEN_CHAT_TOKEN_PREFIX$token", fromUsername)
            .apply()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CHAT_TOKEN, token)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val publicNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_nullchat)
            .setContentTitle("NullChat")
            .setContentText("Nova mensagem privada")
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_nullchat)
            .setLargeIcon(createLargeIcon())
            .setContentTitle("NullChat")
            .setContentText(fromName.ifBlank { "Nova mensagem" })
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setLocalOnly(true)
            .setGroup("chat_messages_group")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPublicVersion(publicNotification)
            .setAllowSystemGeneratedContextualActions(false)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        }
    }

    fun cancelMessage(fromUsername: String) {
        NotificationManagerCompat.from(context).cancel(fromUsername.hashCode())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            channelId,
            "Mensagens NullChat",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificacoes privadas de novas mensagens"
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.new_message}")
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, audioAttributes)
            enableVibration(true)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createLargeIcon(): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round) ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
