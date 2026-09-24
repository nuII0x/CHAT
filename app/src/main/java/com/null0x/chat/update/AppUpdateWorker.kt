package com.null0x.chat.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.null0x.chat.AppBranding
import com.null0x.chat.R
import com.null0x.chat.network.TorHttp
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

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

    private suspend fun checkForUpdate(context: Context): Result {
        return runCatching {
            val manifest = fetchManifest(context)
            AppUpdateManager.recordCheckResult(context, manifest)
            if (manifest != null && manifest.versionCode > com.null0x.chat.BuildConfig.VERSION_CODE) {
                if (AppUpdateManager.isAutoDownloadEnabled(context)) {
                    AppUpdateManager.enqueueDownload(context)
                } else {
                    showUpdateAvailableNotification(context, manifest)
                }
            }
            Result.success()
        }.getOrElse { error ->
            AppUpdateManager.recordCheckResult(context, manifest = null, error = error.message.orEmpty())
            Result.retry()
        }
    }

    private suspend fun downloadUpdate(context: Context): Result {
        val state = AppUpdateManager.refreshState(context)
        val apkUrl = state.apkUrl.takeIf { AppUpdateManager.canUseUpdateDownloadUrl(it) }
            ?: return Result.failure()
        val expectedSha256 = state.apkSha256.lowercase().takeIf { SHA256_REGEX.matches(it) }
            ?: return Result.failure()
        val expectedSize = state.apkSize.takeIf { it in 1..MAX_APK_BYTES }
            ?: return Result.failure()
        val targetFile = File(
            AppUpdateManager.updatesDir(context),
            "NoChat-${state.availableVersionName.ifBlank { state.availableVersionCode.toString() }}.apk"
        )
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
        tempFile.delete()

        return try {
            setForeground(createForegroundInfo(context, "Baixando atualização...", -1, true))
            val connection = TorHttp.openConnection(
                context = context,
                rawUrl = apkUrl,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 45_000
            )
            connection.requestMethod = "GET"
            connection.connect()
            when (connection.responseCode) {
                in 200..299 -> Unit
                in 400..499 -> return Result.failure()
                else -> return Result.retry()
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            if (totalBytes > MAX_APK_BYTES || (totalBytes > 0L && totalBytes != expectedSize)) {
                error("Tamanho do APK difere do manifesto")
            }
            targetFile.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
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
                        if (copiedBytes > expectedSize || copiedBytes > MAX_APK_BYTES) {
                            error("APK excede o tamanho declarado")
                        }
                        digest.update(buffer, 0, read)
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
            if (tempFile.length() != expectedSize) {
                error("Tamanho do APK invalido")
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                error("Hash do APK invalido")
            }
            AppUpdateVerifier.verifyArchive(
                context = context,
                apkFile = tempFile,
                expectedVersionCode = state.availableVersionCode
            ).getOrThrow()
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            AppUpdateManager.recordDownloadComplete(context, targetFile)
            setForeground(createForegroundInfo(context, "Atualização pronta para instalar", 100, false))
            showInstallReadyNotification(context, state.availableVersionName)
            Result.success()
        } catch (error: Exception) {
            tempFile.delete()
            AppUpdateManager.recordDownloadError(context, error.message ?: "Falha ao baixar atualização")
            Result.retry()
        }
    }

    private suspend fun fetchManifest(context: Context): RemoteUpdateManifest {
        require(AppUpdateManager.canUseUpdateUrl(AppUpdateManager.APP_UPDATE_MANIFEST_URL)) {
            "Manifest de atualização precisa ser .onion"
        }
        val connection = TorHttp.openConnection(
            context = context,
            rawUrl = AppUpdateManager.APP_UPDATE_MANIFEST_URL,
            connectTimeoutMs = 12_000,
            readTimeoutMs = 20_000
        )
        connection.requestMethod = "GET"
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
        val apkSha256 = json.optString("apkSha256").trim().lowercase()
        val apkSize = json.optLong("apkSize", 0L)
        require(versionCode > 0) { "versionCode inválido no manifest" }
        require(versionName.isNotBlank()) { "versionName inválido no manifest" }
        require(AppUpdateManager.canUseUpdateDownloadUrl(apkUrl)) {
            "apkUrl precisa ser um endereco onion v3"
        }
        require(SHA256_REGEX.matches(apkSha256)) { "apkSha256 invalido no manifest" }
        require(apkSize in 1..MAX_APK_BYTES) { "apkSize invalido no manifest" }
        return RemoteUpdateManifest(versionCode, versionName, apkUrl, releaseNotes, apkSha256, apkSize)
    }

    private fun showUpdateAvailableNotification(context: Context, manifest: RemoteUpdateManifest) {
        val actionIntent = Intent(context, AppUpdateActionReceiver::class.java).apply {
            action = AppUpdateManager.ACTION_DOWNLOAD_UPDATE
        }
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_nochat,
            "Baixar e instalar",
            PendingIntent.getBroadcast(
                context,
                DOWNLOAD_ACTION_REQUEST,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        ).build()
        val notification = NotificationCompat.Builder(context, ensureChannel(context))
            .setSmallIcon(R.drawable.ic_stat_nochat)
            .setContentTitle("Nova atualização disponível")
            .setContentText("Versão ${manifest.versionName}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(manifest.releaseNotes.ifBlank { "Toque para baixar a atualização." })
            )
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(action)
            .build()
        NotificationManagerCompat.from(context).notify(UPDATE_AVAILABLE_NOTIFICATION_ID, notification)
    }

    private fun showInstallReadyNotification(context: Context, versionName: String) {
        val actionIntent = Intent(context, AppUpdateActionReceiver::class.java).apply {
            action = AppUpdateManager.ACTION_INSTALL_UPDATE
        }
        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_nochat,
            "Instalar",
            PendingIntent.getBroadcast(
                context,
                INSTALL_ACTION_REQUEST,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        ).build()
        val notification = NotificationCompat.Builder(context, ensureChannel(context))
            .setSmallIcon(R.drawable.ic_stat_nochat)
            .setContentTitle("Atualização pronta")
            .setContentText("Versão ${versionName.ifBlank { "nova" }} baixada")
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(action)
            .build()
        NotificationManagerCompat.from(context).notify(INSTALL_READY_NOTIFICATION_ID, notification)
    }

    private fun immutableFlag(): Int {
        return PendingIntent.FLAG_IMMUTABLE
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
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(NOTIFICATION_ID, notification, serviceType)
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
        private const val UPDATE_AVAILABLE_NOTIFICATION_ID = 4802
        private const val INSTALL_READY_NOTIFICATION_ID = 4803
        private const val DOWNLOAD_ACTION_REQUEST = 4804
        private const val INSTALL_ACTION_REQUEST = 4805
        private const val MAX_APK_BYTES = 512L * 1024L * 1024L
        private val SHA256_REGEX = Regex("^[a-f0-9]{64}$")
    }
}
