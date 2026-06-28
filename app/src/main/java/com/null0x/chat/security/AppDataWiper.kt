package com.null0x.chat.security

import android.app.ActivityManager
import android.content.Context
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.TorManager
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import kotlin.system.exitProcess

object AppDataWiper {
    private const val BUFFER_SIZE = 64 * 1024
    private val random = SecureRandom()

    fun wipeAndExit(context: Context): Nothing {
        val appContext = context.applicationContext
        runCatching { ChatNodeManager.stop(appContext) }
        runCatching { TorManager.stop(appContext) }
        runCatching { AppSecurityManager.lock() }

        wipeKnownAppDirectories(appContext)

        val manager = appContext.getSystemService(ActivityManager::class.java)
        if (manager != null) {
            manager.clearApplicationUserData()
        }
        exitProcess(0)
    }

    private fun wipeKnownAppDirectories(context: Context) {
        listOfNotNull(
            context.filesDir,
            context.cacheDir,
            context.noBackupFilesDir,
            context.codeCacheDir,
            context.dataDir
        ).distinctBy { it.absolutePath }
            .sortedByDescending { it.absolutePath.length }
            .forEach { wipeRecursively(it) }
    }

    private fun wipeRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { wipeRecursively(it) }
            runCatching { file.delete() }
            return
        }
        overwriteFile(file)
        runCatching { file.delete() }
    }

    private fun overwriteFile(file: File) {
        if (!file.isFile || !file.canWrite()) return
        val length = file.length()
        if (length <= 0L) return
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                overwritePass(raf, length, FillMode.RANDOM)
                overwritePass(raf, length, FillMode.ZERO)
                overwritePass(raf, length, FillMode.ONE)
                raf.fd.sync()
            }
        }
    }

    private fun overwritePass(file: RandomAccessFile, length: Long, mode: FillMode) {
        file.seek(0L)
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        while (remaining > 0L) {
            val count = minOf(buffer.size.toLong(), remaining).toInt()
            when (mode) {
                FillMode.RANDOM -> random.nextBytes(buffer)
                FillMode.ZERO -> buffer.fill(0)
                FillMode.ONE -> buffer.fill(0xFF.toByte())
            }
            file.write(buffer, 0, count)
            remaining -= count
        }
    }

    private enum class FillMode {
        RANDOM,
        ZERO,
        ONE
    }
}
