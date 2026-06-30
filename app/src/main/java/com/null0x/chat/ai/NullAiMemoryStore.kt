package com.null0x.chat.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.null0x.chat.model.Message
import java.text.Normalizer
import java.util.Locale

private const val NullAiMaxPromptChars = 140

data class NullAiMemorySnapshot(
    val summary: String,
    val importantMemories: List<String>,
    val recentMessages: List<String>
) {
    fun toPromptMemory(): String {
        return buildString {
            if (summary.isNotBlank()) {
                append("Resumo: ").append(summary).append('\n')
            }
            if (importantMemories.isNotEmpty()) {
                append("Lembretes: ").append(importantMemories.joinToString("; ")).append('\n')
            }
            if (recentMessages.isNotEmpty()) {
                append("Ultimas: ").append(recentMessages.joinToString(" | ")).append('\n')
            }
        }.trim().take(NullAiMaxPromptChars)
    }
}

object NullAiMemoryStore {
    private const val DatabaseName = "null_ai_memory.db"
    private const val DatabaseVersion = 1
    private const val DefaultConversation = "null-ai"
    private const val MaxMessageChars = 80
    private const val MaxSummaryChars = 120
    private const val MaxImportantMemories = 8
    private const val MaxStoredMessages = 16
    private val lock = Any()

    fun read(context: Context): String {
        return snapshot(context, DefaultConversation, "").toPromptMemory()
    }

    fun snapshot(context: Context, conversationId: String, query: String): NullAiMemorySnapshot {
        return synchronized(lock) {
            val db = helper(context).readableDatabase
            val conversation = conversationId.ifBlank { DefaultConversation }
            NullAiMemorySnapshot(
                summary = readSummary(db, conversation),
                importantMemories = readImportantMemories(db, conversation, limit = 1),
                recentMessages = readRecentMessages(db, conversation, limit = 1)
            )
        }
    }

    fun update(context: Context, messages: List<Message>) {
        update(context, DefaultConversation, messages)
    }

    fun update(context: Context, conversationId: String, messages: List<Message>) {
        synchronized(lock) {
            val db = helper(context).writableDatabase
            val conversation = conversationId.ifBlank { DefaultConversation }
            db.beginTransaction()
            try {
                messages.takeLast(MaxStoredMessages).forEach { message ->
                    insertMessage(db, conversation, message)
                }
                pruneMessages(db, conversation)
                val recent = readRecentMessages(db, conversation, limit = 2)
                val important = extractImportantMemories(messages)
                important.forEach { insertImportantMemory(db, conversation, it) }
                pruneImportantMemories(db, conversation)
                upsertSummary(db, conversation, buildSummary(recent, readImportantMemories(db, conversation, limit = 1)))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    fun clearConversation(context: Context, conversationId: String) {
        synchronized(lock) {
            val db = helper(context).writableDatabase
            val conversation = conversationId.ifBlank { DefaultConversation }
            db.beginTransaction()
            try {
                db.delete("messages", "conversation_id = ?", arrayOf(conversation))
                db.delete("conversation_summaries", "conversation_id = ?", arrayOf(conversation))
                db.delete("important_memories", "conversation_id = ?", arrayOf(conversation))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun insertMessage(db: SQLiteDatabase, conversationId: String, message: Message) {
        val role = if (message.isMine) "usuario" else "null_ia"
        val cleanText = clean(message.text, MaxMessageChars)
        if (cleanText.isBlank()) return
        val values = ContentValues().apply {
            put("id", message.id)
            put("conversation_id", conversationId)
            put("role", role)
            put("text", cleanText)
            put("timestamp", message.timestamp)
            put("embedding", "")
        }
        db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertImportantMemory(db: SQLiteDatabase, conversationId: String, text: String) {
        val cleanText = clean(text, MaxMessageChars)
        if (cleanText.isBlank()) return
        val normalized = normalize(cleanText)
        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("text", cleanText)
            put("normalized_text", normalized)
            put("weight", 1.0)
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
            put("embedding", "")
        }
        db.insertWithOnConflict("important_memories", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL(
            "UPDATE important_memories SET weight = weight + 0.15, updated_at = ? WHERE conversation_id = ? AND normalized_text = ?",
            arrayOf(System.currentTimeMillis(), conversationId, normalized)
        )
    }

    private fun upsertSummary(db: SQLiteDatabase, conversationId: String, summary: String) {
        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("summary", summary.take(MaxSummaryChars))
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("conversation_summaries", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readSummary(db: SQLiteDatabase, conversationId: String): String {
        return db.rawQuery(
            "SELECT summary FROM conversation_summaries WHERE conversation_id = ? LIMIT 1",
            arrayOf(conversationId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }
    }

    private fun readImportantMemories(db: SQLiteDatabase, conversationId: String, limit: Int): List<String> {
        return db.rawQuery(
            "SELECT text FROM important_memories WHERE conversation_id = ? ORDER BY weight DESC, updated_at DESC LIMIT ?",
            arrayOf(conversationId, limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0).orEmpty())
            }
        }
    }

    private fun readRecentMessages(db: SQLiteDatabase, conversationId: String, limit: Int): List<String> {
        return db.rawQuery(
            "SELECT role, text FROM messages WHERE conversation_id = ? ORDER BY timestamp DESC LIMIT ?",
            arrayOf(conversationId, limit.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val speaker = if (cursor.getString(0) == "usuario") "Usuario" else "Null IA"
                    add("$speaker: ${cursor.getString(1).orEmpty()}")
                }
            }.asReversed()
        }
    }

    private fun pruneMessages(db: SQLiteDatabase, conversationId: String) {
        db.execSQL(
            """
            DELETE FROM messages
            WHERE conversation_id = ? AND id NOT IN (
                SELECT id FROM messages WHERE conversation_id = ? ORDER BY timestamp DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(conversationId, conversationId, MaxStoredMessages.toString())
        )
    }

    private fun pruneImportantMemories(db: SQLiteDatabase, conversationId: String) {
        db.execSQL(
            """
            DELETE FROM important_memories
            WHERE conversation_id = ? AND id NOT IN (
                SELECT id FROM important_memories
                WHERE conversation_id = ?
                ORDER BY weight DESC, updated_at DESC
                LIMIT ?
            )
            """.trimIndent(),
            arrayOf(conversationId, conversationId, MaxImportantMemories.toString())
        )
    }

    private fun buildSummary(recent: List<String>, important: List<String>): String {
        val parts = mutableListOf<String>()
        if (important.isNotEmpty()) {
            parts += "Pontos: ${important.take(1).joinToString("; ")}"
        }
        if (recent.isNotEmpty()) {
            parts += "Recentes: ${recent.takeLast(1).joinToString(" | ")}"
        }
        return parts.joinToString(". ").take(MaxSummaryChars).trim()
    }

    private fun extractImportantMemories(messages: List<Message>): List<String> {
        val triggers = listOf(
            "lembre", "meu nome", "minha", "meu ", "prefiro", "gosto", "nao gosto", "não gosto",
            "sou ", "moro", "trabalho", "estudo", "preciso", "quero que", "modelo", "github"
        )
        return messages
            .takeLast(6)
            .filter { it.isMine }
            .map { clean(it.text, MaxMessageChars) }
            .filter { text ->
                val normalized = normalize(text)
                triggers.any { normalized.contains(it) }
            }
            .distinctBy { normalize(it) }
            .take(2)
    }

    private fun clean(text: String, limit: Int): String {
        return text.trim().replace(Regex("\\s+"), " ").take(limit).trim()
    }

    private fun normalize(text: String): String {
        val ascii = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return ascii.trim()
    }

    private fun helper(context: Context): Helper {
        return Helper.get(context.applicationContext)
    }

    private class Helper private constructor(context: Context) :
        SQLiteOpenHelper(context, DatabaseName, null, DatabaseVersion) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE messages (
                    id TEXT PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    text TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    embedding TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_null_ai_messages_conversation_time ON messages(conversation_id, timestamp)")
            db.execSQL(
                """
                CREATE TABLE conversation_summaries (
                    conversation_id TEXT PRIMARY KEY,
                    summary TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE important_memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT NOT NULL,
                    text TEXT NOT NULL,
                    normalized_text TEXT NOT NULL,
                    weight REAL NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    embedding TEXT NOT NULL,
                    UNIQUE(conversation_id, normalized_text)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_null_ai_memories_conversation ON important_memories(conversation_id, weight, updated_at)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS messages")
            db.execSQL("DROP TABLE IF EXISTS conversation_summaries")
            db.execSQL("DROP TABLE IF EXISTS important_memories")
            onCreate(db)
        }

        companion object {
            @Volatile
            private var instance: Helper? = null

            fun get(context: Context): Helper {
                return instance ?: synchronized(this) {
                    instance ?: Helper(context).also { instance = it }
                }
            }
        }
    }
}
