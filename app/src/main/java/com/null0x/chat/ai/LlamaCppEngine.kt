package com.null0x.chat.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LlamaCppEngine(context: Context) : AiEngine {
    companion object {
        private const val TAG = "LlamaCppEngine"
        private const val MAX_PROMPT_CHARS = 4000

        private val nativeAvailable: Boolean = runCatching {
            System.loadLibrary("llama_jni")
        }.onFailure {
            Log.e(TAG, "Falha ao carregar llama_jni", it)
        }.isSuccess

        private val nativeMutex = Mutex()
    }

    private val appContext = context.applicationContext

    override val activeModel: AiModel
        get() = NullAiModelStore.currentModel(appContext)

    override suspend fun prepare(): Result<Unit> =
    withContext(Dispatchers.Default) {
        nativeMutex.withLock<Result<Unit>> {
            Log.d(TAG, "prepare() iniciado")

            if (!nativeAvailable) {
                Result.failure(
                    IllegalStateException("A biblioteca nativa llama_jni ainda nao carregou.")
                )
            } else if (!NullAiModelStore.hasUsableModel(appContext)) {
                Result.failure(
                    IllegalStateException("Escolha um arquivo .gguf em Configuracoes > Null IA.")
                )
            } else {
                val model = activeModel
                Log.d(TAG, "Preparando modelo: ${model.path}")

                runCatching {
                    val error = nativePrepare(model.path).trim()
                    if (error.isNotBlank()) {
                        throw IllegalStateException(error)
                    }

                    Log.d(TAG, "prepare() concluido")
                    Unit
                }.onFailure {
                    Log.e(TAG, "prepare() falhou", it)
                }
            }
        }
    }

    override suspend fun generateReply(prompt: String): Result<String> = withContext(Dispatchers.Default) {
        generateReplyWithStats(prompt).map { it.text }
    }

    suspend fun generateReplyWithStats(prompt: String): Result<AiGeneration> = withContext(Dispatchers.Default) {
        nativeMutex.withLock {
            val cleanPrompt = prompt.trim()

            Log.d(TAG, "generateReply() chamado. promptLength=${cleanPrompt.length}")

            if (cleanPrompt.isBlank()) {
                return@withLock Result.success(
                    AiGeneration(text = "Me manda uma mensagem e eu respondo por aqui.")
                )
            }

            if (!nativeAvailable) {
                return@withLock Result.success(
                    AiGeneration(
                        text = "Null IA encontrou o chat local, mas a biblioteca nativa llama_jni ainda nao carregou."
                    )
                )
            }

            if (!NullAiModelStore.hasUsableModel(appContext)) {
                return@withLock Result.success(
                    AiGeneration(
                        text = "Escolha um arquivo .gguf em Configuracoes > Modelo da Null IA para eu responder localmente."
                    )
                )
            }

            val model = activeModel
            val safePrompt = cleanPrompt.takeLast(MAX_PROMPT_CHARS)

            if (safePrompt.length != cleanPrompt.length) {
                Log.w(
                    TAG,
                    "Prompt reduzido de ${cleanPrompt.length} para ${safePrompt.length} caracteres"
                )
            }

            Log.d(TAG, "Gerando com modelo: ${model.path}")

            runCatching {
                parseNativeGeneration(
                    nativeGenerate(model.path, safePrompt)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?: "Nao consegui gerar uma resposta agora."
                )
            }.onSuccess {
                Log.d(TAG, "generateReply() concluido. replyLength=${it.text.length}")
            }.onFailure {
                Log.e(TAG, "generateReply() falhou", it)
            }
        }
    }

    private fun parseNativeGeneration(raw: String): AiGeneration {
        val marker = "__NULLAI_METRICS__"
        val lines = raw.lines()
        val metricsLine = lines.firstOrNull { it.startsWith(marker) }
        val text = lines
            .filterNot { it.startsWith(marker) }
            .joinToString("\n")
            .trim()
            .ifBlank { "Nao consegui gerar uma resposta agora." }
        val metrics = metricsLine
            ?.removePrefix(marker)
            ?.trim()
            ?.split(' ')
            ?.mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "").trim()
                val value = part.substringAfter('=', missingDelimiterValue = "").trim().toLongOrNull()
                if (key.isBlank() || value == null) null else key to value
            }
            ?.toMap()
            .orEmpty()
        return AiGeneration(
            text = text,
            totalMs = metrics["total_ms"],
            promptEvalMs = metrics["prompt_eval_ms"],
            decodeMs = metrics["decode_ms"],
            promptTokens = metrics["prompt_tokens"],
            outputBytes = metrics["output_bytes"],
            gpu = metrics["gpu"]?.let { it == 1L },
            gpuAvailable = metrics["gpu_available"]?.let { it == 1L },
            cpuThreads = metrics["cpu_threads"]
        )
    }

    private external fun nativePrepare(modelPath: String): String

    private external fun nativeGenerate(modelPath: String, prompt: String): String
}

data class AiGeneration(
    val text: String,
    val totalMs: Long? = null,
    val promptEvalMs: Long? = null,
    val decodeMs: Long? = null,
    val promptTokens: Long? = null,
    val outputBytes: Long? = null,
    val gpu: Boolean? = null,
    val gpuAvailable: Boolean? = null,
    val cpuThreads: Long? = null
) {
    fun timingNotice(): String? {
        val total = totalMs ?: return null
        val parts = buildList {
            add("pensou em ${formatDuration(total)}")
            promptEvalMs?.let { add("prompt ${formatDuration(it)}") }
            decodeMs?.let { add("resposta ${formatDuration(it)}") }
            promptTokens?.let { add("$it tokens") }
            gpu?.let { add(if (it) "GPU ativa" else "CPU fallback") }
            if (gpu == false && gpuAvailable == true) {
                add("GPU recusou o modelo")
            }
            cpuThreads?.let { add("CPU ${it}t") }
        }
        return parts.joinToString(" • ")
    }

    private fun formatDuration(ms: Long): String {
        return if (ms >= 1000L) {
            val seconds = ms / 1000.0
            String.format(java.util.Locale.US, "%.1fs", seconds)
        } else {
            "${ms}ms"
        }
    }
}
