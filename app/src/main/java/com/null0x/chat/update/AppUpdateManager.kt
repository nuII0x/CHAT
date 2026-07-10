package com.null0x.chat.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.null0x.chat.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object AppUpdateManager {
    // Troque pelo endereço onion real do seu servidor de atualização.
    const val APP_UPDATE_MANIFEST_URL = "http://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion/update.json"
    const val ACTION_DOWNLOAD_UPDATE = "com.null0x.chat.action.DOWNLOAD_UPDATE"
    const val ACTION_INSTALL_UPDATE = "com.null0x.chat.action.INSTALL_UPDATE"

    private const val PrefsName = "app_updates"
    private const val CheckWorkName = "app_update_check"
    private const val DownloadWorkName = "app_update_download"
    private const val AutoDownloadEnabledKey = "auto_download_enabled"
    private const val AvailableVersionCodeKey = "available_version_code"
    private const val AvailableVersionNameKey = "available_version_name"
    private const val ApkUrlKey = "apk_url"
    private const val ReleaseNotesKey = "release_notes"
    private const val DownloadedApkPathKey = "downloaded_apk_path"
    private const val LastCheckedAtKey = "last_checked_at"
    private const val StatusKey = "status"
    private const val ErrorKey = "error"
    private const val StatusIdle = "idle"
    private const val StatusAvailable = "available"
    private const val StatusDownloading = "downloading"
    private const val StatusDownloaded = "downloaded"
    private const val StatusError = "error"
    internal const val InputModeKey = "mode"
    internal const val InputModeCheck = "check"
    internal const val InputModeDownload = "download"

    private val _state = MutableStateFlow<AppUpdateState?>(null)
    val state: StateFlow<AppUpdateState?> = _state.asStateFlow()

    fun initialize(context: Context) {
        refreshState(context)
    }

    fun refreshState(context: Context): AppUpdateState {
        return readState(context.applicationContext).also { _state.value = it }
    }

    fun isAutoDownloadEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(AutoDownloadEnabledKey, false)
    }

    fun setAutoDownloadEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        prefs(appContext).edit().putBoolean(AutoDownloadEnabledKey, enabled).apply()
        refreshState(appContext)
        if (enabled) {
            enqueueCheck(appContext)
        } else {
            WorkManager.getInstance(appContext).cancelUniqueWork(CheckWorkName)
        }
    }

    fun enqueueCheck(context: Context) {
        val appContext = context.applicationContext
        if (!isAutoDownloadEnabled(appContext)) return
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setInputData(Data.Builder().putString(InputModeKey, InputModeCheck).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            CheckWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueDownload(context: Context) {
        val appContext = context.applicationContext
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setInputData(Data.Builder().putString(InputModeKey, InputModeDownload).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            DownloadWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
        prefs(appContext).edit()
            .putString(StatusKey, StatusDownloading)
            .putString(ErrorKey, "")
            .apply()
        refreshState(appContext)
    }

    fun canUseUpdateUrl(rawUrl: String): Boolean {
        return runCatching {
            val uri = Uri.parse(rawUrl.trim())
            val host = uri.host.orEmpty().lowercase()
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
                host.endsWith(".onion") &&
                host.length == 62
        }.getOrDefault(false)
    }

    fun canUseUpdateDownloadUrl(rawUrl: String): Boolean {
        return runCatching {
            val uri = Uri.parse(rawUrl.trim())
            uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        }.getOrDefault(false)
    }

    fun installDownloadedUpdate(context: Context): Result<Unit> {
        val appContext = context.applicationContext
        val state = readState(appContext)
        val apkFile = state.downloadedApkPath.takeIf { it.isNotBlank() }?.let(::File)
            ?: return Result.failure(IllegalStateException("Nenhuma atualização baixada"))
        if (!apkFile.isFile) {
            return Result.failure(IllegalStateException("Arquivo da atualização não encontrado"))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !appContext.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${appContext.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            return Result.failure(IllegalStateException("Permita instalar apps desconhecidos e toque em instalar novamente"))
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(intent)
        return Result.success(Unit)
    }

    internal fun updatesDir(context: Context): File {
        return File(context.applicationContext.filesDir, "app-updates").apply { mkdirs() }
    }

    internal fun recordCheckResult(
        context: Context,
        manifest: RemoteUpdateManifest?,
        error: String = ""
    ) {
        val appContext = context.applicationContext
        val editor = prefs(appContext).edit()
            .putLong(LastCheckedAtKey, System.currentTimeMillis())
            .putString(ErrorKey, error)
        if (manifest != null && manifest.versionCode > BuildConfig.VERSION_CODE) {
            editor
                .putInt(AvailableVersionCodeKey, manifest.versionCode)
                .putString(AvailableVersionNameKey, manifest.versionName)
                .putString(ApkUrlKey, manifest.apkUrl)
                .putString(ReleaseNotesKey, manifest.releaseNotes)
                .putString(StatusKey, StatusAvailable)
                .putString(DownloadedApkPathKey, "")
        } else if (error.isBlank()) {
            editor
                .putInt(AvailableVersionCodeKey, 0)
                .putString(AvailableVersionNameKey, "")
                .putString(ApkUrlKey, "")
                .putString(ReleaseNotesKey, "")
                .putString(DownloadedApkPathKey, "")
                .putString(StatusKey, StatusIdle)
        } else {
            editor.putString(StatusKey, StatusError)
        }
        editor.apply()
        refreshState(appContext)
    }

    internal fun recordDownloadComplete(context: Context, apkFile: File) {
        val appContext = context.applicationContext
        prefs(appContext).edit()
            .putString(DownloadedApkPathKey, apkFile.absolutePath)
            .putString(StatusKey, StatusDownloaded)
            .putString(ErrorKey, "")
            .apply()
        refreshState(appContext)
    }

    internal fun recordDownloadError(context: Context, message: String) {
        val appContext = context.applicationContext
        prefs(appContext).edit()
            .putString(StatusKey, StatusError)
            .putString(ErrorKey, message)
            .apply()
        refreshState(appContext)
    }

    private fun readState(context: Context): AppUpdateState {
        val prefs = prefs(context)
        return AppUpdateState(
            currentVersionCode = BuildConfig.VERSION_CODE,
            currentVersionName = BuildConfig.VERSION_NAME,
            autoDownloadEnabled = prefs.getBoolean(AutoDownloadEnabledKey, false),
            availableVersionCode = prefs.getInt(AvailableVersionCodeKey, 0),
            availableVersionName = prefs.getString(AvailableVersionNameKey, "").orEmpty(),
            apkUrl = prefs.getString(ApkUrlKey, "").orEmpty(),
            releaseNotes = prefs.getString(ReleaseNotesKey, "").orEmpty(),
            downloadedApkPath = prefs.getString(DownloadedApkPathKey, "").orEmpty(),
            lastCheckedAt = prefs.getLong(LastCheckedAtKey, 0L),
            status = prefs.getString(StatusKey, StatusIdle).orEmpty(),
            error = prefs.getString(ErrorKey, "").orEmpty()
        )
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
}

data class AppUpdateState(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val autoDownloadEnabled: Boolean,
    val availableVersionCode: Int,
    val availableVersionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val downloadedApkPath: String,
    val lastCheckedAt: Long,
    val status: String,
    val error: String
) {
    val hasNewVersion: Boolean get() = availableVersionCode > currentVersionCode
    val isDownloading: Boolean get() = status == "downloading"
    val isDownloaded: Boolean get() = status == "downloaded" && downloadedApkPath.isNotBlank()
    val hasError: Boolean get() = status == "error" && error.isNotBlank()
}

internal data class RemoteUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)
