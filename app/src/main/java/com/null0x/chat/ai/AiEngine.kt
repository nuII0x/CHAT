package com.null0x.chat.ai

interface AiEngine {
    val activeModel: AiModel

    suspend fun prepare(): Result<Unit>

    suspend fun generateReply(prompt: String): Result<String>

    fun shutdown()
}

data class AiModel(
    val id: String,
    val label: String,
    val path: String
)
