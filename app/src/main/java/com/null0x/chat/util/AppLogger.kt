package com.null0x.chat.util

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Logger de diagnostico persistido no armazenamento interno privado do aplicativo.
 *
 * Nunca envie textos de mensagens, rotas onion completas, tokens, senhas ou chaves para
 * este logger. As escritas sao serializadas fora da thread chamadora para nao bloquear a UI.
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val CURRENT_FILE = "app-debug.log"
    private const val OLD_FILE = "app-debug-old.log"
    private const val LOG_DIRECTORY = "logs"
    private const val MAX_MESSAGE_LENGTH = 16_384

    // 5 MB por arquivo; no maximo dois arquivos sao mantidos.
    private const val MAX_FILE_SIZE = 5L * 1024L * 1024L

    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(256),
        { task -> Thread(task, "app-log-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    fun d(context: Context, message: String) {
        enqueue(context, "DEBUG", message, null)
        Log.d(TAG, message)
    }

    fun i(context: Context, message: String) {
        enqueue(context, "INFO", message, null)
        Log.i(TAG, message)
    }

    fun w(context: Context, message: String, error: Throwable? = null) {
        enqueue(context, "WARN", message, error)
        Log.w(TAG, message, error)
    }

    fun e(context: Context, message: String, error: Throwable? = null) {
        enqueue(context, "ERROR", message, error)
        Log.e(TAG, message, error)
    }

    private fun enqueue(
        context: Context,
        level: String,
        message: String,
        error: Throwable?
    ) {
        val directory = File(context.applicationContext.filesDir, LOG_DIRECTORY)
        val safeMessage = sanitize(message)
        val callerThread = Thread.currentThread().name
        writer.execute {
            write(directory, level, safeMessage, error, callerThread)
        }
    }

    private fun write(
        directory: File,
        level: String,
        message: String,
        throwable: Throwable?,
        callerThread: String
    ) {
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Nao foi possivel criar o diretorio de logs")
            }

            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).format(Date())
            val entry = buildString {
                append(timestamp)
                append(" | ")
                append(level)
                append(" | PID=")
                append(Process.myPid())
                append(" | THREAD=")
                append(callerThread)
                append(" | ")
                appendLine(message)

                if (throwable != null) {
                    appendLine(redactSecrets(Log.getStackTraceString(throwable)).take(MAX_MESSAGE_LENGTH * 4))
                }
            }

            val currentFile = File(directory, CURRENT_FILE)
            rotateIfNeeded(directory, currentFile, entry.toByteArray(Charsets.UTF_8).size)
            currentFile.appendText(entry, Charsets.UTF_8)
        } catch (loggingError: Throwable) {
            Log.e(TAG, "Falha ao gravar log em arquivo", loggingError)
        }
    }

    private fun rotateIfNeeded(directory: File, currentFile: File, pendingBytes: Int) {
        if (!currentFile.exists() || currentFile.length() + pendingBytes <= MAX_FILE_SIZE) return

        val oldFile = File(directory, OLD_FILE)
        if (oldFile.exists() && !oldFile.delete()) {
            Log.w(TAG, "Nao foi possivel remover o arquivo de log antigo")
            return
        }
        if (!currentFile.renameTo(oldFile)) {
            Log.w(TAG, "Nao foi possivel rotacionar o arquivo de log atual")
        }
    }

    private fun sanitize(message: String): String {
        return redactSecrets(message)
            .replace('\r', ' ')
            .replace('\n', ' ')
            .take(MAX_MESSAGE_LENGTH)
    }

    private fun redactSecrets(value: String): String {
        return value
            .replace(Regex("(?i)\\b[a-z2-7]{56}\\.onion\\b"), "[ROTA_ONION_OCULTA]")
            .replace(
                Regex("(?i)(password|senha|token|secret|private[_-]?key)(\\s*[=:]\\s*)[^\\s,;]+"),
                "\$1\$2[OCULTO]"
            )
    }

    fun deviceInfo(): String = buildString {
        append("Fabricante: ${Build.MANUFACTURER}")
        append(" | Modelo: ${Build.MODEL}")
        append(" | Android: ${Build.VERSION.RELEASE}")
        append(" | SDK: ${Build.VERSION.SDK_INT}")
    }
}
