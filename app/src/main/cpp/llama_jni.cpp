// llama_jni.cpp
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <sys/stat.h>

#include "llama.h"

namespace {

constexpr const char * TAG = "NullAi";
constexpr int CONTEXT_TOKENS = 512;
constexpr int MAX_PROMPT_TOKENS = 384;
constexpr int MAX_PROMPT_HEAD_TOKENS = 96;
constexpr int MAX_PREDICT_TOKENS = 96;
constexpr int BATCH_TOKENS = 32;
constexpr unsigned int CPU_LOAD_LIMIT_PERCENT = 60;
constexpr unsigned int MAX_CPU_THREADS = 3;
constexpr float UI_RELIEF_CPU_BUSY_PERCENT = 72.0f;
constexpr int UI_RELIEF_LIGHT_PAUSE_MS = 2;
constexpr int UI_RELIEF_HEAVY_PAUSE_MS = 6;

std::mutex g_mutex;

llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;

std::string g_model_path;
long long g_model_size = 0;
long long g_model_mtime = 0;

bool g_backend_initialized = false;
bool g_model_uses_accelerator = false;
long long g_last_cpu_total = 0;
long long g_last_cpu_idle = 0;

int preferred_thread_count();
long long elapsed_millis_since(const std::chrono::steady_clock::time_point & start);
const char * backend_device_type_name(enum ggml_backend_dev_type type);
void log_backend_devices();
void adaptive_ui_relief_pause();

bool file_metadata(const std::string & path, long long & size, long long & mtime) {
    struct stat info {};
    if (stat(path.c_str(), &info) != 0) {
        size = 0;
        mtime = 0;
        return false;
    }

    size = static_cast<long long>(info.st_size);
    mtime = static_cast<long long>(info.st_mtime);
    return true;
}

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return "";
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring make_jstring(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}

void unload_model() {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    g_model_path.clear();
    g_model_size = 0;
    g_model_mtime = 0;
    g_model_uses_accelerator = false;
}

void shutdown_backend() {
    unload_model();

    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }

}

void init_backend() {
    if (g_backend_initialized) {
        return;
    }

    shutdown_backend();

    llama_backend_init();

    g_backend_initialized = true;

    log_backend_devices();

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "llama.cpp backend initialized mode=cpu"
    );
}

bool ensure_model_loaded(const std::string & model_path, std::string & error) {
    if (!g_backend_initialized) {
        init_backend();
    }

    if (model_path.empty()) {
        error = "modelo GGUF nao selecionado.";
        return false;
    }

    long long model_size = 0;
    long long model_mtime = 0;
    file_metadata(model_path, model_size, model_mtime);

    if (g_model != nullptr &&
        g_model_path == model_path &&
        g_model_size == model_size &&
        g_model_mtime == model_mtime) {
        return true;
    }

    unload_model();

    const auto load_started_at = std::chrono::steady_clock::now();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(model_path.c_str(), model_params);

    if (g_model == nullptr) {
        error = "nao consegui carregar o modelo GGUF.";
        return false;
    }

    g_model_path = model_path;
    g_model_size = model_size;
    g_model_mtime = model_mtime;
    g_model_uses_accelerator = false;

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "model load done elapsed_ms=%lld mode=cpu_only",
        elapsed_millis_since(load_started_at)
    );

    return true;
}

bool ensure_context_ready(std::string & error) {
    if (g_model == nullptr) {
        error = "modelo GGUF nao carregado.";
        return false;
    }

    if (g_ctx != nullptr) {
        const auto clear_started_at = std::chrono::steady_clock::now();
        const int threads = preferred_thread_count();

        llama_set_n_threads(g_ctx, threads, threads);
        llama_memory_clear(llama_get_memory(g_ctx), true);

        __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "context reuse clear elapsed_ms=%lld threads=%d",
            elapsed_millis_since(clear_started_at),
            threads
        );

        return true;
    }

    const auto context_started_at = std::chrono::steady_clock::now();

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = CONTEXT_TOKENS;
    ctx_params.n_batch = BATCH_TOKENS;
    ctx_params.n_ubatch = BATCH_TOKENS;
    ctx_params.n_threads = preferred_thread_count();
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.no_perf = true;

    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (g_ctx == nullptr) {
        error = "nao consegui criar o contexto do modelo.";
        return false;
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "context create done elapsed_ms=%lld n_ctx=%d n_batch=%d threads=%d gpu=%d",
        elapsed_millis_since(context_started_at),
        CONTEXT_TOKENS,
        BATCH_TOKENS,
        ctx_params.n_threads,
        g_model_uses_accelerator ? 1 : 0
    );

    return true;
}

int preferred_thread_count() {
    const unsigned int available = std::thread::hardware_concurrency();
    if (available <= 2) return 1;

    const unsigned int allowed_by_percent = std::max(
        1u,
        (available * CPU_LOAD_LIMIT_PERCENT) / 100u
    );

    const unsigned int mobile_safe_cap = std::min(
        allowed_by_percent,
        MAX_CPU_THREADS
    );

    return static_cast<int>(mobile_safe_cap);
}

bool read_cpu_totals(long long & total, long long & idle) {
    std::FILE * file = std::fopen("/proc/stat", "r");
    if (file == nullptr) return false;

    char label[8] = {};
    long long user = 0;
    long long nice = 0;
    long long system = 0;
    long long idle_ticks = 0;
    long long iowait = 0;
    long long irq = 0;
    long long softirq = 0;
    long long steal = 0;
    const int read = std::fscanf(
        file,
        "%7s %lld %lld %lld %lld %lld %lld %lld %lld",
        label,
        &user,
        &nice,
        &system,
        &idle_ticks,
        &iowait,
        &irq,
        &softirq,
        &steal
    );
    std::fclose(file);

    if (read < 8) return false;
    idle = idle_ticks + iowait;
    total = user + nice + system + idle_ticks + iowait + irq + softirq + steal;
    return total > 0;
}

void adaptive_ui_relief_pause() {
    long long total = 0;
    long long idle = 0;
    if (!read_cpu_totals(total, idle)) return;

    const long long previous_total = g_last_cpu_total;
    const long long previous_idle = g_last_cpu_idle;
    g_last_cpu_total = total;
    g_last_cpu_idle = idle;

    if (previous_total <= 0 || total <= previous_total) return;

    const long long total_delta = total - previous_total;
    const long long idle_delta = idle - previous_idle;
    if (total_delta <= 0) return;

    const float busy_percent = 100.0f * static_cast<float>(total_delta - idle_delta) /
        static_cast<float>(total_delta);
    if (busy_percent < UI_RELIEF_CPU_BUSY_PERCENT) return;

    const int pause_ms = busy_percent >= 88.0f ? UI_RELIEF_HEAVY_PAUSE_MS : UI_RELIEF_LIGHT_PAUSE_MS;
    std::this_thread::sleep_for(std::chrono::milliseconds(pause_ms));
}

long long elapsed_millis_since(const std::chrono::steady_clock::time_point & start) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start
    ).count();
}

const char * backend_device_type_name(enum ggml_backend_dev_type type) {
    switch (type) {
        case GGML_BACKEND_DEVICE_TYPE_CPU:
            return "cpu";
        case GGML_BACKEND_DEVICE_TYPE_GPU:
            return "gpu";
        case GGML_BACKEND_DEVICE_TYPE_IGPU:
            return "igpu";
        case GGML_BACKEND_DEVICE_TYPE_ACCEL:
            return "accelerator";
        case GGML_BACKEND_DEVICE_TYPE_META:
            return "meta";
    }

    return "unknown";
}

void log_backend_devices() {
    const size_t count = ggml_backend_dev_count();

    for (size_t index = 0; index < count; ++index) {
        ggml_backend_dev_t device = ggml_backend_dev_get(index);

        if (device == nullptr) {
            continue;
        }

        const enum ggml_backend_dev_type type = ggml_backend_dev_type(device);
        const char * name = ggml_backend_dev_name(device);
        const char * description = ggml_backend_dev_description(device);

        size_t memory_free = 0;
        size_t memory_total = 0;

        ggml_backend_dev_memory(device, &memory_free, &memory_total);

        __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "backend device index=%zu type=%s name=%s description=%s memory_free_mb=%zu memory_total_mb=%zu",
            index,
            backend_device_type_name(type),
            name != nullptr ? name : "",
            description != nullptr ? description : "",
            memory_free / (1024 * 1024),
            memory_total / (1024 * 1024)
        );
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "backend policy mode=cpu cpu_thread_limit_percent=%u cpu_thread_cap=%u",
        CPU_LOAD_LIMIT_PERCENT,
        MAX_CPU_THREADS
    );
}

std::vector<llama_token> tokenize_prompt(
    const llama_vocab * vocab,
    const std::string & prompt,
    std::string & error
) {
    const int n_prompt = -llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );

    if (n_prompt <= 0) {
        error = "nao consegui tokenizar a mensagem.";
        return {};
    }

    std::vector<llama_token> tokens(n_prompt);

    const int written = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        true
    );

    if (written < 0) {
        error = "falha ao preparar os tokens da mensagem.";
        return {};
    }

    return tokens;
}

std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(256);

    int n = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        0,
        true
    );

    if (n < 0) {
        buffer.resize(static_cast<size_t>(-n));

        n = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            true
        );
    }

    if (n <= 0) return "";

    return std::string(buffer.data(), static_cast<size_t>(n));
}

void trim_whitespace_inplace(std::string & value) {
    while (!value.empty() &&
        (value.front() == ' ' ||
         value.front() == '\n' ||
         value.front() == '\r' ||
         value.front() == '\t')) {
        value.erase(value.begin());
    }

    while (!value.empty() &&
        (value.back() == ' ' ||
         value.back() == '\n' ||
         value.back() == '\r' ||
         value.back() == '\t')) {
        value.pop_back();
    }
}

void cut_at_marker(std::string & value, const std::string & marker) {
    const size_t pos = value.find(marker);

    if (pos != std::string::npos) {
        value = value.substr(0, pos);
    }
}

std::string format_chat_prompt(const std::string & user_prompt) {
    const std::string system_prompt =
        "Voce e a Null IA. Responda em portugues do Brasil, seja direto e "
        "use qualquer resultado exato fornecido pelo app sem recalcular errado.";

    const llama_chat_message messages[] = {
        { "system", system_prompt.c_str() },
        { "user", user_prompt.c_str() }
    };

    const char * tmpl = llama_model_chat_template(g_model, nullptr);
    const int32_t needed = llama_chat_apply_template(
        tmpl,
        messages,
        2,
        true,
        nullptr,
        0
    );

    if (needed > 0) {
        std::vector<char> buffer(static_cast<size_t>(needed) + 1, '\0');
        const int32_t written = llama_chat_apply_template(
            tmpl,
            messages,
            2,
            true,
            buffer.data(),
            static_cast<int32_t>(buffer.size())
        );

        if (written > 0) {
            return std::string(buffer.data());
        }
    }

    return system_prompt + "\nUsuario: " + user_prompt + "\nNull IA:";
}

void compact_prompt_tokens(std::vector<llama_token> & prompt_tokens) {
    if (static_cast<int>(prompt_tokens.size()) <= MAX_PROMPT_TOKENS) {
        return;
    }

    const int head_count = std::min(MAX_PROMPT_HEAD_TOKENS, MAX_PROMPT_TOKENS / 2);
    const int tail_count = MAX_PROMPT_TOKENS - head_count;

    std::vector<llama_token> compact_prompt;
    compact_prompt.reserve(MAX_PROMPT_TOKENS);
    compact_prompt.insert(
        compact_prompt.end(),
        prompt_tokens.begin(),
        prompt_tokens.begin() + head_count
    );
    compact_prompt.insert(
        compact_prompt.end(),
        prompt_tokens.end() - tail_count,
        prompt_tokens.end()
    );

    __android_log_print(
        ANDROID_LOG_WARN,
        TAG,
        "prompt compacted from_tokens=%zu to_tokens=%zu head=%d tail=%d",
        prompt_tokens.size(),
        compact_prompt.size(),
        head_count,
        tail_count
    );

    prompt_tokens.swap(compact_prompt);
}

std::string sanitize_reply(std::string output) {
    trim_whitespace_inplace(output);

    const std::vector<std::string> leading_labels = {
        "Null IA:",
        "Resposta:",
        "Assistente:",
        "Assistant:",
        "AI:"
    };

    bool stripped_label = true;

    while (stripped_label) {
        stripped_label = false;

        for (const auto & label : leading_labels) {
            if (output.rfind(label, 0) == 0) {
                output.erase(0, label.size());
                trim_whitespace_inplace(output);
                stripped_label = true;
            }
        }
    }

    const std::vector<std::string> markers = {
        "\nUsuario:",
        "\nUser:",
        "\nNull IA:",
        "\nResposta:",
        "\nMensagem:",
        "\nAssistente:",
        "\nAssistant:",
        "\nAI:",
        "<|im_end|>",
        "<|eot_id|>",
        "<|end|>",
        "<|endoftext|>",
        "</s>"
    };

    for (const auto & marker : markers) {
        cut_at_marker(output, marker);
    }

    trim_whitespace_inplace(output);

    if (output.find('\n') != std::string::npos) {
        std::string compact;
        compact.reserve(output.size());

        bool seen_content = false;

        for (const char ch : output) {
            if (ch == '\n' || ch == '\r') {
                if (seen_content) {
                    compact.push_back(' ');
                }

                continue;
            }

            compact.push_back(ch);
            seen_content = true;
        }

        output.swap(compact);
        trim_whitespace_inplace(output);
    }

    return output;
}

std::string generate_reply_locked(
    const std::string & model_path,
    const std::string & user_prompt
) {
    const auto total_started_at = std::chrono::steady_clock::now();

    std::string error;

    if (!ensure_model_loaded(model_path, error)) {
        return "Null IA: " + error;
    }

    const std::string prompt = format_chat_prompt(user_prompt);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    const auto tokenize_started_at = std::chrono::steady_clock::now();

    std::vector<llama_token> prompt_tokens = tokenize_prompt(
        vocab,
        prompt,
        error
    );

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "tokenize done elapsed_ms=%lld prompt_bytes=%zu tokens=%zu",
        elapsed_millis_since(tokenize_started_at),
        prompt.size(),
        prompt_tokens.size()
    );

    if (!error.empty()) {
        return "Null IA: " + error;
    }

    if (prompt_tokens.empty()) {
        return "Null IA: prompt vazio.";
    }

    compact_prompt_tokens(prompt_tokens);

    if (static_cast<int>(prompt_tokens.size()) + MAX_PREDICT_TOKENS + 8 >= CONTEXT_TOKENS) {
        return "Null IA: essa mensagem ficou grande demais para o contexto configurado.";
    }

    if (!ensure_context_ready(error)) {
        return "Null IA: " + error;
    }

    llama_sampler * sampler = llama_sampler_chain_init(
        llama_sampler_chain_default_params()
    );

    if (sampler == nullptr) {
        return "Null IA: nao consegui criar o sampler.";
    }

    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.92f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.65f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    int n_pos = 0;

    const auto prompt_eval_started_at = std::chrono::steady_clock::now();

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "generate start prompt_tokens=%zu n_ctx=%d n_batch=%d max_predict=%d threads=%d accelerator=%d",
        prompt_tokens.size(),
        CONTEXT_TOKENS,
        BATCH_TOKENS,
        MAX_PREDICT_TOKENS,
        preferred_thread_count(),
        g_model_uses_accelerator ? 1 : 0
    );

    if (llama_model_has_encoder(g_model)) {
        llama_batch encoder_batch = llama_batch_get_one(
            prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size())
        );

        if (llama_encode(g_ctx, encoder_batch) != 0) {
            llama_sampler_free(sampler);
            return "Null IA: falha ao avaliar o prompt.";
        }

        llama_token decoder_start_token_id = llama_model_decoder_start_token(g_model);

        if (decoder_start_token_id == LLAMA_TOKEN_NULL) {
            decoder_start_token_id = llama_vocab_bos(vocab);
        }

        llama_batch decoder_batch = llama_batch_get_one(
            &decoder_start_token_id,
            1
        );

        if (llama_decode(g_ctx, decoder_batch) != 0) {
            llama_sampler_free(sampler);
            return "Null IA: falha ao iniciar o decoder.";
        }

        n_pos = 1;
    } else {
        int offset = 0;

        const int total_prompt_tokens = static_cast<int>(prompt_tokens.size());
        const int max_batch_tokens = BATCH_TOKENS;

        while (offset < total_prompt_tokens) {
            const int remaining = total_prompt_tokens - offset;
            const int chunk_size = std::min(max_batch_tokens, remaining);

            __android_log_print(
                ANDROID_LOG_INFO,
                TAG,
                "decode prompt chunk offset=%d chunk_size=%d total=%d",
                offset,
                chunk_size,
                total_prompt_tokens
            );

            llama_batch prompt_batch = llama_batch_get_one(
                prompt_tokens.data() + offset,
                static_cast<int32_t>(chunk_size)
            );

            if (llama_decode(g_ctx, prompt_batch) != 0) {
                llama_sampler_free(sampler);
                return "Null IA: falha ao avaliar o prompt.";
            }

            offset += chunk_size;
            n_pos += chunk_size;

            if (n_pos >= CONTEXT_TOKENS - 1) {
                llama_sampler_free(sampler);
                return "Null IA: o prompt ocupou todo o contexto do modelo.";
            }

            adaptive_ui_relief_pause();
        }
    }

    const long long prompt_eval_ms = elapsed_millis_since(prompt_eval_started_at);

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "prompt eval done elapsed_ms=%lld tokens=%zu n_pos=%d",
        prompt_eval_ms,
        prompt_tokens.size(),
        n_pos
    );

    const auto decode_started_at = std::chrono::steady_clock::now();

    for (int n_decode = 0; n_decode < MAX_PREDICT_TOKENS; ++n_decode) {
        llama_token next_token = llama_sampler_sample(sampler, g_ctx, -1);

        if (llama_vocab_is_eog(vocab, next_token)) {
            break;
        }

        const std::string piece = token_to_piece(vocab, next_token);
        output += piece;

        if (output.find("\nUsuario:") != std::string::npos ||
            output.find("\nNull IA:") != std::string::npos ||
            output.find("\nMensagem:") != std::string::npos ||
            output.find("\nResposta:") != std::string::npos) {
            break;
        }

        if (n_pos >= CONTEXT_TOKENS - 1) {
            break;
        }

        llama_batch token_batch = llama_batch_get_one(&next_token, 1);

        if (llama_decode(g_ctx, token_batch) != 0) {
            output = "Null IA: falha durante a geracao.";
            break;
        }

        n_pos += 1;
        adaptive_ui_relief_pause();
    }

    llama_sampler_free(sampler);

    const long long decode_ms = elapsed_millis_since(decode_started_at);

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "decode done elapsed_ms=%lld output_bytes=%zu",
        decode_ms,
        output.size()
    );

    output = sanitize_reply(output);

    if (output.empty()) {
        return "Nao consegui responder bem agora. Tente uma mensagem mais curta.";
    }

    const long long total_ms = elapsed_millis_since(total_started_at);
    const size_t output_bytes = output.size();

    __android_log_print(
        ANDROID_LOG_INFO,
        TAG,
        "generate done total_ms=%lld prompt_eval_ms=%lld decode_ms=%lld output_bytes=%zu",
        total_ms,
        prompt_eval_ms,
        decode_ms,
        output_bytes
    );

    return "__NULLAI_METRICS__ total_ms=" + std::to_string(total_ms) +
        " prompt_eval_ms=" + std::to_string(prompt_eval_ms) +
        " decode_ms=" + std::to_string(decode_ms) +
        " prompt_tokens=" + std::to_string(prompt_tokens.size()) +
        " output_bytes=" + std::to_string(output_bytes) +
        " gpu=" + std::to_string(g_model_uses_accelerator ? 1 : 0) +
        " gpu_available=0" +
        " cpu_threads=" + std::to_string(preferred_thread_count()) +
        "\n" + output;
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativePrepare(
    JNIEnv * env,
    jobject,
    jstring j_model_path,
    jboolean j_use_gpu
) {
    const std::string model_path = jstring_to_string(env, j_model_path);
    (void) j_use_gpu;

    std::string error;

    std::lock_guard<std::mutex> lock(g_mutex);

    init_backend();

    if (!ensure_model_loaded(model_path, error)) {
        return make_jstring(env, error);
    }

    if (!ensure_context_ready(error)) {
        return make_jstring(env, error);
    }

    return make_jstring(env, "");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativeIsSupported(
    JNIEnv *,
    jobject
) {
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativeGenerate(
    JNIEnv * env,
    jobject,
    jstring j_model_path,
    jstring j_prompt
) {
    const std::string model_path = jstring_to_string(env, j_model_path);
    const std::string prompt = jstring_to_string(env, j_prompt);

    if (prompt.empty()) {
        return make_jstring(env, "Me manda uma mensagem e eu respondo por aqui.");
    }

    std::unique_lock<std::mutex> lock(g_mutex, std::try_to_lock);

    if (!lock.owns_lock()) {
        return make_jstring(env, "Ainda estou terminando a resposta anterior. Tente de novo em alguns segundos.");
    }

    return make_jstring(env, generate_reply_locked(model_path, prompt));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativeShutdown(
    JNIEnv *,
    jobject
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_model();
}
