package com.null0x.chat.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LlamaCppEngine(context: Context) : AiEngine {
    companion object {
        private val nativeAvailable: Boolean = runCatching {
            System.loadLibrary("llama_jni")
        }.isSuccess
    }

    private val appContext = context.applicationContext

    override val activeModel: AiModel
        get() = NullAiModelStore.currentModel(appContext)

    override suspend fun prepare(): Result<Unit> = withContext(Dispatchers.Default) {
        if (!nativeAvailable) {
            return@withContext Result.failure(
                IllegalStateException("A biblioteca nativa llama_jni ainda nao carregou.")
            )
        }

        if (!NullAiModelStore.hasUsableModel(appContext)) {
            return@withContext Result.failure(
                IllegalStateException("Escolha um arquivo .gguf em Configuracoes > Null IA.")
            )
        }

        runCatching {
            val error = nativePrepare(activeModel.path).trim()
            if (error.isNotBlank()) {
                throw IllegalStateException(error)
            }
        }
    }

    override suspend fun generateReply(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return@withContext Result.success("Me manda uma mensagem e eu respondo por aqui.")
        }

        if (!nativeAvailable) {
            return@withContext Result.success(
                "Null IA encontrou o chat local, mas a biblioteca nativa llama_jni ainda nao carregou."
            )
        }

        if (!NullAiModelStore.hasUsableModel(appContext)) {
            return@withContext Result.success(
                "Escolha um arquivo .gguf em Configuracoes > Modelo da Null IA para eu responder localmente."
            )
        }

        runCatching {
            nativeGenerate(activeModel.path, cleanPrompt).trim()
                .takeIf { it.isNotBlank() }
                ?: "Nao consegui gerar uma resposta agora."
        }
    }

    private external fun nativePrepare(modelPath: String): String

    private external fun nativeGenerate(modelPath: String, prompt: String): String
}
