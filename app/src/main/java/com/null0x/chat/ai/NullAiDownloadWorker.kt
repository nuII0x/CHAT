package com.null0x.chat.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.null0x.chat.AppBranding
import com.null0x.chat.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class NullAiDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val downloadUrl = NullAiModelStore.modelDownloadUrl(context)
        if (downloadUrl.isBlank()) return Result.success()
        if (!downloadUrl.startsWith("http://", ignoreCase = true) &&
            !downloadUrl.startsWith("https://", ignoreCase = true)
        ) {
            return Result.failure()
        }

        val targetFile = NullAiModelStore.defaultModelFile(context)
        targetFile.parentFile?.mkdirs()

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        tempFile.delete()

        return try {
            setForeground(createForegroundInfo(context, "Baixando modelo da Null IA...", -1, true))

            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.useCaches = false

            connection.connect()
            val responseCode = connection.responseCode
            when (responseCode) {
                in 200..299 -> Unit
                in 400..499 -> return Result.failure()
                else -> return Result.retry()
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copiedBytes = 0L
                    var lastProgress = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copiedBytes += read

                        val progress = if (totalBytes > 0L) {
                            ((copiedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        if (progress != lastProgress) {
                            lastProgress = progress
                            val message = if (progress >= 0) {
                                "Baixando modelo da Null IA... $progress%"
                            } else {
                                "Baixando modelo da Null IA... ${formatBytes(copiedBytes)}"
                            }
                            setProgress(
                                workDataOf(
                                    "downloadedBytes" to copiedBytes,
                                    "totalBytes" to totalBytes,
                                    "progress" to progress
                                )
                            )
                            setForeground(createForegroundInfo(context, message, progress, totalBytes <= 0L))
                        }
                    }
                }
            }

            if (tempFile.length() <= 0L) {
                throw IllegalStateException("Arquivo baixado vazio")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            NullAiModelStore.persistDownloadedModel(context, targetFile)
            Result.success()
        } catch (error: java.net.MalformedURLException) {
            tempFile.delete()
            Result.failure()
        } catch (error: IllegalArgumentException) {
            tempFile.delete()
            Result.failure()
        } catch (error: Exception) {
            tempFile.delete()
            Result.retry()
        }
    }

    private fun createForegroundInfo(
        context: Context,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, ensureChannel(context))
            .setSmallIcon(R.drawable.ic_stat_nochat)
            .setContentTitle("Null IA")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceAtLeast(0), indeterminate)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads da Null IA",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mostra o progresso do download do modelo da Null IA"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        return CHANNEL_ID
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    companion object {
        private const val CHANNEL_ID = "null_ai_downloads"
        private const val NOTIFICATION_ID = 4701
    }
}
