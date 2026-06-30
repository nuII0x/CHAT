package com.null0x.chat.update

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
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val mode = inputData.getString(AppUpdateManager.InputModeKey) ?: AppUpdateManager.InputModeCheck
        return when (mode) {
            AppUpdateManager.InputModeDownload -> downloadUpdate(context)
            else -> checkForUpdate(context)
        }
    }

    private fun checkForUpdate(context: Context): Result {
        return runCatching {
            val manifest = fetchManifest()
            AppUpdateManager.recordCheckResult(context, manifest)
            Result.success()
        }.getOrElse { error ->
            AppUpdateManager.recordCheckResult(context, manifest = null, error = error.message.orEmpty())
            Result.retry()
        }
    }

    private suspend fun downloadUpdate(context: Context): Result {
        val state = AppUpdateManager.refreshState(context)
        val apkUrl = state.apkUrl.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
            ?: return Result.failure()
        val targetFile = File(
            AppUpdateManager.updatesDir(context),
            "NoChat-${state.availableVersionName.ifBlank { state.availableVersionCode.toString() }}.apk"
        )
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        tempFile.delete()

        return try {
            setForeground(createForegroundInfo(context, "Baixando atualização...", -1, true))
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.useCaches = false
            connection.connect()
            when (connection.responseCode) {
                in 200..299 -> Unit
                in 400..499 -> return Result.failure()
                else -> return Result.retry()
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            targetFile.parentFile?.mkdirs()
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
                            setProgress(
                                workDataOf(
                                    "downloadedBytes" to copiedBytes,
                                    "totalBytes" to totalBytes,
                                    "progress" to progress
                                )
                            )
                            val text = if (progress >= 0) {
                                "Baixando atualização... $progress%"
                            } else {
                                "Baixando atualização... ${formatBytes(copiedBytes)}"
                            }
                            setForeground(createForegroundInfo(context, text, progress, totalBytes <= 0L))
                        }
                    }
                }
            }
            if (tempFile.length() <= 0L) {
                error("APK baixado vazio")
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            AppUpdateManager.recordDownloadComplete(context, targetFile)
            setForeground(createForegroundInfo(context, "Atualização pronta para instalar", 100, false))
            Result.success()
        } catch (error: Exception) {
            tempFile.delete()
            AppUpdateManager.recordDownloadError(context, error.message ?: "Falha ao baixar atualização")
            Result.retry()
        }
    }

    private fun fetchManifest(): RemoteUpdateManifest {
        val connection = URL(AppUpdateManager.APP_UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.useCaches = false
        connection.connect()
        if (connection.responseCode !in 200..299) {
            error("Manifest de atualização indisponível: HTTP ${connection.responseCode}")
        }
        val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(body)
        val versionCode = json.optInt("versionCode", 0)
        val versionName = json.optString("versionName").trim()
        val apkUrl = json.optString("apkUrl").trim()
        val releaseNotes = json.optString("releaseNotes").trim()
        require(versionCode > 0) { "versionCode inválido no manifest" }
        require(versionName.isNotBlank()) { "versionName inválido no manifest" }
        require(apkUrl.startsWith("http://", true) || apkUrl.startsWith("https://", true)) {
            "apkUrl inválido no manifest"
        }
        return RemoteUpdateManifest(versionCode, versionName, apkUrl, releaseNotes)
    }

    private fun createForegroundInfo(
        context: Context,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, ensureChannel(context))
            .setSmallIcon(R.drawable.ic_stat_nochat)
            .setContentTitle(AppBranding.APP_NAME)
            .setContentText(text)
            .setOngoing(!text.contains("pronta", ignoreCase = true))
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
            "Atualizações do app",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mostra o progresso do download de atualizações"
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
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4801
    }
}
