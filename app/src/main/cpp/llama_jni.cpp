#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

namespace {

constexpr const char * TAG = "NullAi";
constexpr int MAX_CONTEXT_TOKENS = 512;
constexpr int MAX_PREDICT_TOKENS = 36;
constexpr int BATCH_TOKENS = 128;

std::mutex g_mutex;
std::once_flag g_backend_once;
llama_model * g_model = nullptr;
std::string g_model_path;

void init_backend_once() {
    std::call_once(g_backend_once, [] {
        ggml_backend_load_all();
        llama_backend_init();
        __android_log_print(ANDROID_LOG_INFO, TAG, "llama.cpp backend initialized");
    });
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
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_model_path.clear();
}

bool ensure_model_loaded(const std::string & model_path, std::string & error) {
    init_backend_once();

    if (model_path.empty()) {
        error = "modelo GGUF nao selecionado.";
        return false;
    }

    if (g_model != nullptr && g_model_path == model_path) {
        return true;
    }

    unload_model();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (g_model == nullptr) {
        error = "nao consegui carregar o modelo GGUF.";
        return false;
    }

    g_model_path = model_path;
    return true;
}

int preferred_thread_count() {
    const unsigned int available = std::thread::hardware_concurrency();
    if (available <= 1) return 1;
    return 2;
}

std::vector<llama_token> tokenize_prompt(const llama_vocab * vocab, const std::string & prompt, std::string & error) {
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
    int n = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (n < 0) {
        buffer.resize(static_cast<size_t>(-n));
        n = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    }
    if (n <= 0) return "";
    return std::string(buffer.data(), static_cast<size_t>(n));
}

void trim_whitespace_inplace(std::string & value) {
    while (!value.empty() && (value.front() == ' ' || value.front() == '\n' || value.front() == '\r' || value.front() == '\t')) {
        value.erase(value.begin());
    }
    while (!value.empty() && (value.back() == ' ' || value.back() == '\n' || value.back() == '\r' || value.back() == '\t')) {
        value.pop_back();
    }
}

void cut_at_marker(std::string & value, const std::string & marker) {
    const size_t pos = value.find(marker);
    if (pos != std::string::npos) {
        value = value.substr(0, pos);
    }
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
        "\nAI:"
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

std::string generate_reply_locked(const std::string & model_path, const std::string & user_prompt) {
    std::string error;
    if (!ensure_model_loaded(model_path, error)) {
        return "Null IA: " + error;
    }

    const std::string prompt = user_prompt;

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::vector<llama_token> prompt_tokens = tokenize_prompt(vocab, prompt, error);
    if (!error.empty()) {
        return "Null IA: " + error;
    }

    const int requested_context = static_cast<int>(prompt_tokens.size()) + MAX_PREDICT_TOKENS + 8;
    const int n_ctx = std::min(MAX_CONTEXT_TOKENS, std::max(256, requested_context));
    if (static_cast<int>(prompt_tokens.size()) >= n_ctx) {
        return "Null IA: essa mensagem ficou grande demais para o contexto configurado.";
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(n_ctx);
    ctx_params.n_batch = static_cast<uint32_t>(std::min(BATCH_TOKENS, n_ctx));
    ctx_params.n_ubatch = ctx_params.n_batch;
    ctx_params.n_threads = preferred_thread_count();
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.no_perf = true;

    llama_context * ctx = llama_init_from_model(g_model, ctx_params);
    if (ctx == nullptr) {
        return "Null IA: nao consegui criar o contexto do modelo.";
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    llama_batch batch = llama_batch_get_one(
        prompt_tokens.data(),
        static_cast<int32_t>(prompt_tokens.size())
    );

    if (llama_model_has_encoder(g_model)) {
        if (llama_encode(ctx, batch) != 0) {
            llama_sampler_free(sampler);
            llama_free(ctx);
            return "Null IA: falha ao avaliar o prompt.";
        }

        llama_token decoder_start_token_id = llama_model_decoder_start_token(g_model);
        if (decoder_start_token_id == LLAMA_TOKEN_NULL) {
            decoder_start_token_id = llama_vocab_bos(vocab);
        }
        batch = llama_batch_get_one(&decoder_start_token_id, 1);
    }

    std::string output;
    int n_pos = 0;

    for (int n_decode = 0; n_decode < MAX_PREDICT_TOKENS; ++n_decode) {
        if (llama_decode(ctx, batch) != 0) {
            output = "Null IA: falha durante a geracao.";
            break;
        }

        n_pos += batch.n_tokens;
        llama_token next_token = llama_sampler_sample(sampler, ctx, -1);
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

        batch = llama_batch_get_one(&next_token, 1);
        if (n_pos >= n_ctx - 1) {
            break;
        }
    }

    llama_sampler_free(sampler);
    llama_free(ctx);

    output = sanitize_reply(output);

    if (output.empty()) {
        return "Null IA: o modelo nao gerou resposta.";
    }

    return output;
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_null0x_chat_ai_LlamaCppEngine_nativePrepare(
    JNIEnv * env,
    jobject,
    jstring j_model_path
) {
    const std::string model_path = jstring_to_string(env, j_model_path);
    std::string error;

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!ensure_model_loaded(model_path, error)) {
        return make_jstring(env, error);
    }

    return make_jstring(env, "");
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

    std::lock_guard<std::mutex> lock(g_mutex);
    return make_jstring(env, generate_reply_locked(model_path, prompt));
}
