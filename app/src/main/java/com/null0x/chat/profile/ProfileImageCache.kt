package com.null0x.chat.profile

import android.content.Context
import com.null0x.chat.network.TorHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object ProfileImageCache {
    private const val MAX_IMAGE_BYTES = 2L * 1024L * 1024L

    suspend fun refresh(context: Context, route: String, imageUrl: String): String {
        val cleanUrl = imageUrl.trim()
        if (!isSupportedRemoteUrl(cleanUrl)) return ""
        val appContext = context.applicationContext
        val cacheFile = cacheFileFor(appContext, route, cleanUrl)
        if (cacheFile.isFile && cacheFile.length() > 0L) return cacheFile.absolutePath

        return withContext(Dispatchers.IO) {
            val directory = cacheDirectory(appContext)
            directory.mkdirs()
            val partial = File(directory, "${cacheFile.name}.part")
            val connection = TorHttp.openConnection(
                appContext,
                cleanUrl,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 20_000
            )
            try {
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) {
                                throw IllegalStateException("Imagem de perfil maior que o limite local")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (partial.length() <= 0L) return@withContext ""
                clearRouteImages(directory, route, keep = cacheFile.name)
                if (cacheFile.exists()) cacheFile.delete()
                partial.renameTo(cacheFile)
                cacheFile.absolutePath
            } finally {
                partial.delete()
                connection.disconnect()
            }
        }
    }

    fun cachedPath(context: Context, route: String, imageUrl: String): String {
        val cleanUrl = imageUrl.trim()
        if (!isSupportedRemoteUrl(cleanUrl)) return ""
        val file = cacheFileFor(context.applicationContext, route, cleanUrl)
        return if (file.isFile && file.length() > 0L) file.absolutePath else ""
    }

    private fun cacheFileFor(context: Context, route: String, imageUrl: String): File {
        val routeHash = sha256(route.trim().lowercase()).take(16)
        val urlHash = sha256(imageUrl.trim()).take(32)
        return File(cacheDirectory(context), "${routeHash}_${urlHash}.img")
    }

    private fun cacheDirectory(context: Context): File {
        return File(context.cacheDir, "profile_images")
    }

    private fun clearRouteImages(directory: File, route: String, keep: String) {
        val prefix = "${sha256(route.trim().lowercase()).take(16)}_"
        directory.listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name != keep }
            ?.forEach { it.delete() }
    }

    private fun isSupportedRemoteUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("http://", ignoreCase = true)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
