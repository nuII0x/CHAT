package com.null0x.chat.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import org.json.JSONObject
import com.null0x.chat.AppBranding
import com.null0x.chat.MainActivity
import com.null0x.chat.R
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.profile.ProfileImageCache
import com.null0x.chat.security.AppSecurityManager
import java.util.UUID

class MessageNotifier(private val context: Context) {

    companion object {
        private const val ACTION_OPEN_CHAT = "com.null0x.chat.action.OPEN_CHAT"
        const val ACTION_REPLY = "com.null0x.chat.action.REPLY"
        private const val EXTRA_OPEN_CHAT_TOKEN = "extra_open_chat_token"
        const val EXTRA_REPLY_USERNAME = "extra_reply_username"
        private const val OPEN_CHAT_PREFS = "notification_open_chat"
        private const val PENDING_PREFS = "notification_pending_messages"
        private const val OPEN_CHAT_TOKEN_PREFIX = "token:"
        private const val PENDING_PREFIX = "pending:"
        private const val DUPLICATE_NOTIFICATION_WINDOW_MS = 2_000L
        private const val MAX_RECENT_NOTIFICATION_KEYS = 64
        const val KEY_TEXT_REPLY = "key_text_reply"
        private val recentNotificationLock = Any()
        private val recentNotificationTimestamps = LinkedHashMap<String, Long>(MAX_RECENT_NOTIFICATION_KEYS, 0.75f, true)

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

    private val channelId = AppBranding.internalId("messages_v3")

    init {
        createChannel()
    }

    fun showMessage(fromUsername: String, fromName: String, text: String) {
        if (isRecentDuplicate(fromUsername, text)) return

        val token = UUID.randomUUID().toString()
        registerOpenChatToken(token, fromUsername)
        storePendingMessage(fromUsername, fromName, text)

        if (!canPostNotifications()) return

        postNotification(fromUsername, fromName, text, token)
    }

    fun cancelMessage(fromUsername: String) {
        val notificationId = fromUsername.hashCode()
        NotificationManagerCompat.from(context).cancel(notificationId)
        clearPendingMessage(fromUsername)
    }

    fun clearAll() {
        NotificationManagerCompat.from(context).cancelAll()
        context.applicationContext.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun restorePendingNotifications() {
        if (!canPostNotifications()) return
        val prefs = context.applicationContext.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        prefs.all.values.forEach { raw ->
            val rawJson = raw as? String ?: return@forEach
            val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return@forEach
            val fromUsername = json.optString("fromUsername").trim()
            if (fromUsername.isBlank()) return@forEach
            val token = UUID.randomUUID().toString()
            registerOpenChatToken(token, fromUsername)
            postNotification(
                fromUsername = fromUsername,
                fromName = json.optString("fromName").trim(),
                text = json.optString("text").trim(),
                token = token,
                silent = true
            )
        }
    }

    private fun postNotification(
        fromUsername: String,
        fromName: String,
        text: String,
        token: String,
        silent: Boolean = false
    ) {
        val notificationId = fromUsername.hashCode()

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
        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_REPLY_USERNAME, fromUsername)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableReplyFlag()
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_nochat,
            "Responder",
            replyPendingIntent
        )
            .addRemoteInput(
                RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel("Responder")
                    .build()
            )
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
        val largeIcon = profileLargeIcon(fromUsername) ?: createLargeIcon()
        val senderName = fromName.ifBlank { AppBranding.APP_NAME }
        val senderPerson = Person.Builder()
            .setName(senderName)
            .apply {
                largeIcon?.let { setIcon(IconCompat.createWithBitmap(it)) }
            }
            .build()
        val localPerson = Person.Builder()
            .setName(AppBranding.APP_NAME)
            .build()
        val publicNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_nochat)
            .setContentTitle(AppBranding.APP_NAME)
            .setContentText("Nova mensagem privada")
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_nochat)
            .setLargeIcon(largeIcon)
            .setContentTitle(senderName)
            .setContentText(text.trim().ifBlank { "Nova mensagem" }.take(120))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setLocalOnly(true)
            .setGroup("chat_messages_group")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPublicVersion(publicNotification)
            .setSilent(silent)
            .setAllowSystemGeneratedContextualActions(false)
            .addAction(replyAction)
            .setStyle(
                NotificationCompat.MessagingStyle(localPerson)
                    .addMessage(
                        text.trim().ifBlank { "Nova mensagem" },
                        System.currentTimeMillis(),
                        senderPerson
                    )
            )
        if (!silent && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setSound(messageSoundUri())
        }
        val notification = notificationBuilder.build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        }
    }

    private fun isRecentDuplicate(fromUsername: String, text: String): Boolean {
        val now = System.currentTimeMillis()
        val key = "${fromUsername.trim()}|${text.trim()}"
        synchronized(recentNotificationLock) {
            val lastSeenAt = recentNotificationTimestamps[key]
            if (lastSeenAt != null && now - lastSeenAt <= DUPLICATE_NOTIFICATION_WINDOW_MS) {
                return true
            }
            recentNotificationTimestamps[key] = now
            pruneRecentNotifications(now)
            return false
        }
    }

    private fun pruneRecentNotifications(now: Long) {
        val iterator = recentNotificationTimestamps.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val expired = now - entry.value > DUPLICATE_NOTIFICATION_WINDOW_MS * 4
            val oversized = recentNotificationTimestamps.size > MAX_RECENT_NOTIFICATION_KEYS
            if (expired || oversized) {
                iterator.remove()
            } else {
                break
            }
        }
    }

    private fun storePendingMessage(fromUsername: String, fromName: String, text: String) {
        val prefs = context.applicationContext.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val json = JSONObject()
            .put("fromUsername", fromUsername)
            .put("fromName", fromName)
            .put("text", text)
        prefs.edit()
            .putString("$PENDING_PREFIX${fromUsername.hashCode()}", json.toString())
            .apply()
    }

    private fun registerOpenChatToken(token: String, fromUsername: String) {
        context.getSharedPreferences(OPEN_CHAT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$OPEN_CHAT_TOKEN_PREFIX$token", fromUsername)
            .apply()
    }

    private fun clearPendingMessage(fromUsername: String) {
        context.applicationContext.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$PENDING_PREFIX${fromUsername.hashCode()}")
            .apply()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            channelId,
            "Mensagens ${AppBranding.APP_NAME}",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificacoes privadas de novas mensagens"
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(messageSoundUri(), audioAttributes)
            enableVibration(true)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun messageSoundUri(): Uri {
        return Uri.parse("android.resource://${context.packageName}/${R.raw.new_message}")
    }

    private fun canPostNotifications(): Boolean {
        if (!AppSecurityManager.isUnlocked()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
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

    private fun profileLargeIcon(fromUsername: String): Bitmap? {
        val profile = ChatNodeManager.publicProfileFor(fromUsername) ?: return null
        val imagePath = ProfileImageCache.cachedPath(context, profile.route, profile.imageUrl)
        if (imagePath.isBlank()) return null
        return decodeNotificationIcon(imagePath)
    }

    private fun decodeNotificationIcon(path: String, targetSizePx: Int = 192): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
        val sampleSize = Integer.highestOneBit((largestSide / targetSizePx).coerceAtLeast(1))
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun mutableReplyFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    }
}
