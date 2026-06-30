package com.null0x.chat.ai

import android.content.Context
import com.null0x.chat.model.Message
import java.io.File

object NullAiMemoryStore {
    private const val MaxStoredChars = 1_200
    private const val MaxMessageChars = 180
    private const val MaxTurns = 8

    fun read(context: Context): String {
        val file = memoryFile(context)
        if (!file.isFile) return ""
        return runCatching { file.readText().trim().take(MaxStoredChars) }.getOrDefault("")
    }

    fun update(context: Context, messages: List<Message>) {
        val compact = messages
            .takeLast(MaxTurns)
            .mapNotNull { message ->
                val text = message.text
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .take(MaxMessageChars)
                if (text.isBlank()) return@mapNotNull null
                val speaker = if (message.isMine) "Usuario" else "Null IA"
                "$speaker: $text"
            }
            .joinToString(separator = "\n")
            .takeLast(MaxStoredChars)
            .trim()

        val file = memoryFile(context)
        file.parentFile?.mkdirs()
        if (compact.isBlank()) {
            file.delete()
        } else {
            file.writeText(compact)
        }
    }

    private fun memoryFile(context: Context): File {
        return File(context.applicationContext.filesDir, "null_ai/memory.txt")
    }
}
