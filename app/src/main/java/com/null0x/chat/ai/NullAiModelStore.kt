package com.null0x.chat.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object NullAiModelStore {
    private const val RecommendedModelDownloadUrl =
        "https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/Qwen2.5-0.5B-Instruct-Q2_K.gguf"
    private const val PrefsName = "null_ai"
    private const val DownloadWorkName = "null_ai_model_download"
    private const val DefaultModelId = "local-gguf"
    private const val CustomModelId = "custom-gguf"
    private const val ModelIdKey = "model_id"
    private const val ModelLabelKey = "model_label"
    private const val ModelPathKey = "model_path"
    private const val AutoDownloadEnabledKey = "auto_download_enabled"
    private const val ModelDownloadUrlKey = "model_download_url"

    fun currentModel(context: Context): AiModel {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        val customPath = prefs.getString(ModelPathKey, null)?.takeIf { it.isNotBlank() }
        if (customPath != null && File(customPath).isFile) {
            return AiModel(
                id = prefs.getString(ModelIdKey, CustomModelId).orEmpty().ifBlank { CustomModelId },
                label = prefs.getString(ModelLabelKey, null)?.takeIf { it.isNotBlank() }
                    ?: File(customPath).name,
                path = customPath
            )
        }

        val defaultFile = File(modelsDir(appContext), "null-ai.gguf")
        if (defaultFile.isFile) {
            return AiModel(
                id = DefaultModelId,
                label = "Modelo GGUF local",
                path = defaultFile.absolutePath
            )
        }
        return AiModel(
            id = DefaultModelId,
            label = "Modelo GGUF local",
            path = defaultFile.absolutePath
        )
    }

    fun hasUsableModel(context: Context): Boolean {
        return File(currentModel(context).path).isFile
    }

    fun modelDownloadUrl(context: Context): String {
        return prefs(context).getString(ModelDownloadUrlKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: RecommendedModelDownloadUrl
    }

    fun setModelDownloadUrl(context: Context, url: String) {
        val cleanUrl = url.trim()
        prefs(context).edit().putString(ModelDownloadUrlKey, cleanUrl).apply()
        if (isAutoDownloadEnabled(context) && canDownloadFromUrl(cleanUrl)) {
            scheduleAutoDownload(context)
        }
    }

    fun isAutoDownloadEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(AutoDownloadEnabledKey, false)
    }

    fun setAutoDownloadEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(AutoDownloadEnabledKey, enabled).apply()
        if (enabled) {
            scheduleAutoDownload(context)
        } else {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(DownloadWorkName)
        }
    }

    fun scheduleAutoDownload(context: Context) {
        val appContext = context.applicationContext
        if (!isAutoDownloadEnabled(appContext)) return
        if (!canDownloadFromUrl(modelDownloadUrl(appContext))) return

        enqueueModelDownloadInternal(appContext, NetworkType.UNMETERED)
    }

    fun enqueueModelDownload(context: Context) {
        enqueueModelDownloadInternal(context.applicationContext, NetworkType.UNMETERED)
    }

    fun enqueueModelDownloadNow(context: Context) {
        enqueueModelDownloadInternal(context.applicationContext, NetworkType.CONNECTED)
    }

    private fun enqueueModelDownloadInternal(appContext: Context, networkType: NetworkType) {
        val url = modelDownloadUrl(appContext)
        if (!canDownloadFromUrl(url)) return

        val request = OneTimeWorkRequestBuilder<NullAiDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            DownloadWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun defaultModelFile(context: Context): File {
        return File(modelsDir(context), "null-ai.gguf")
    }

    fun persistDownloadedModel(context: Context, file: File) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val defaultFile = defaultModelFile(appContext)
        val activePath = prefs.getString(ModelPathKey, null).orEmpty()
        if (activePath.isBlank() || activePath == defaultFile.absolutePath) {
            prefs.edit()
                .putString(ModelIdKey, DefaultModelId)
                .putString(ModelLabelKey, "Modelo GGUF local")
                .putString(ModelPathKey, file.absolutePath)
                .apply()
        }
    }

    suspend fun importModel(context: Context, uri: Uri): Result<AiModel> = withContext(Dispatchers.IO) {
        runCatching {
            val appContext = context.applicationContext
            val displayName = displayNameFor(appContext, uri)
            val safeName = safeGgufFileName(displayName)
            val destination = File(modelsDir(appContext), safeName)
            val temp = File(destination.parentFile, "$safeName.tmp")

            temp.delete()
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Nao consegui abrir o arquivo selecionado.")

            if (temp.length() <= 0L) {
                temp.delete()
                error("O arquivo GGUF selecionado esta vazio.")
            }

            if (destination.exists()) {
                destination.delete()
            }
            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }

            val model = AiModel(
                id = CustomModelId,
                label = destination.name,
                path = destination.absolutePath
            )

            appContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
                .edit()
                .putString(ModelIdKey, model.id)
                .putString(ModelLabelKey, model.label)
                .putString(ModelPathKey, model.path)
                .apply()

            model
        }
    }

    private fun modelsDir(context: Context): File {
        return File(context.filesDir, "models").apply { mkdirs() }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    private fun displayNameFor(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        return cursor.getString(index).orEmpty()
                    }
                }
            }
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }

    private fun safeGgufFileName(rawName: String): String {
        val clean = rawName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.', '-')
            .ifBlank { "null-ai.gguf" }

        require(clean.endsWith(".gguf", ignoreCase = true)) {
            "Escolha um arquivo .gguf."
        }

        return clean
    }

    private fun canDownloadFromUrl(url: String): Boolean {
        val clean = url.trim()
        return (clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true)) &&
            clean.contains(".gguf", ignoreCase = true)
    }
}
