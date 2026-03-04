#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include "llama.h"
#include "logging.h"

struct LlamaState {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
    llama_sampler* thinkSampler = nullptr;
    std::vector<llama_token> tokens;
    std::atomic<bool> cancelled{false};
    bool isThinking = false;
    llama_token thinkStartToken = -1;
    llama_token thinkEndToken = -1;
    int promptTokenCount = 0;
    float tokensPerSecond = 0.0f;
    int generatedTokenCount = 0;
    int64_t genStartTimeMs = 0;
} g_state;

static llama_sampler* createChatSampler(const llama_model* model) {
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* chain = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.8f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return chain;
}

static llama_sampler* createThinkSampler(const llama_model* model) {
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* chain = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.6f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    return chain;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeInit(JNIEnv* env, jobject thiz, jstring nativeLibDir) {
    const char* libDir = env->GetStringUTFChars(nativeLibDir, nullptr);
    LOGI("nativeInit: libDir=%s", libDir);
    llama_backend_init();
    env->ReleaseStringUTFChars(nativeLibDir, libDir);
}

JNIEXPORT jboolean JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeLoadModel(JNIEnv* env, jobject thiz,
        jstring modelPath, jint nThreads, jint nCtx, jint nBatch) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s, threads=%d, ctx=%d, batch=%d", path, nThreads, nCtx, nBatch);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mlock = false;

    g_state.model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_state.model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t)nCtx;
    cparams.n_batch = (uint32_t)nBatch;
    cparams.n_threads = (uint32_t)nThreads;
    cparams.n_threads_batch = (uint32_t)nThreads;

    g_state.ctx = llama_init_from_model(g_state.model, cparams);
    if (!g_state.ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_state.model);
        g_state.model = nullptr;
        return JNI_FALSE;
    }

    if (g_state.sampler) { llama_sampler_free(g_state.sampler); }
    if (g_state.thinkSampler) { llama_sampler_free(g_state.thinkSampler); }
    g_state.sampler = createChatSampler(g_state.model);
    g_state.thinkSampler = createThinkSampler(g_state.model);
    g_state.cancelled = false;
    g_state.isThinking = false;
    // Auto-detect <think>/<\/think> token IDs from this model's vocabulary.
    // Works for any model family — no hardcoded IDs.
    {
        const llama_vocab* vocab = llama_model_get_vocab(g_state.model);
        if (vocab) {
            llama_token tmp[4];
            int n = llama_tokenize(vocab, "<think>", 7, tmp, 4, false, true);
            g_state.thinkStartToken = (n == 1) ? tmp[0] : -1;
            n = llama_tokenize(vocab, "</think>", 8, tmp, 4, false, true);
            g_state.thinkEndToken = (n == 1) ? tmp[0] : -1;
            LOGI("Think tokens: start=%d, end=%d", g_state.thinkStartToken, g_state.thinkEndToken);
        }
    }
    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativePreparePrompt(JNIEnv* env, jobject thiz, jstring formattedPrompt) {
    const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
    if (!vocab) {
        LOGE("nativePreparePrompt: failed to get vocab");
        return;
    }
    const char* prompt = env->GetStringUTFChars(formattedPrompt, nullptr);

    // Tokenize — first call to get count
    int n_tokens = -llama_tokenize(vocab, prompt, strlen(prompt), nullptr, 0, true, true);
    env->ReleaseStringUTFChars(formattedPrompt, prompt);

    g_state.tokens.resize(n_tokens);
    const char* prompt2 = env->GetStringUTFChars(formattedPrompt, nullptr);
    llama_tokenize(vocab, prompt2, strlen(prompt2), g_state.tokens.data(), n_tokens, true, true);
    env->ReleaseStringUTFChars(formattedPrompt, prompt2);

    g_state.promptTokenCount = n_tokens;
    g_state.generatedTokenCount = 0;
    g_state.cancelled = false;
    g_state.isThinking = false;

    // Clear KV cache and run prefill
    llama_memory_clear(llama_get_memory(g_state.ctx), true);

    llama_batch batch = llama_batch_get_one(g_state.tokens.data(), g_state.tokens.size());
    if (llama_decode(g_state.ctx, batch) != 0) {
        LOGE("nativePreparePrompt: llama_decode failed");
    }
    g_state.genStartTimeMs = llama_time_us() / 1000;
    LOGI("Prompt prepared: %d tokens", n_tokens);
}

JNIEXPORT jstring JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeNextToken(JNIEnv* env, jobject thiz) {
    if (g_state.cancelled.load()) return nullptr;
    if (!g_state.ctx || !g_state.model) return nullptr;
    const llama_vocab * vocab = llama_model_get_vocab(g_state.model);
    if (!vocab) {
        LOGE("nativeNextToken: failed to get vocab");
        return nullptr;
    }

    // Use appropriate sampler
    llama_sampler* currentSampler = g_state.isThinking ? g_state.thinkSampler : g_state.sampler;
    llama_token id = llama_sampler_sample(currentSampler, g_state.ctx, -1);

    // End of generation
    if (llama_vocab_is_eog(vocab, id)) {
        LOGI("EOS reached after %d tokens", g_state.generatedTokenCount);
        return nullptr;
    }

    // think-end token — ID auto-detected from vocabulary at load time
    if (g_state.thinkEndToken != -1 && id == g_state.thinkEndToken) {
        g_state.isThinking = false;
        llama_sampler_accept(currentSampler, id);
        llama_batch batch = llama_batch_get_one(&id, 1);
        llama_decode(g_state.ctx, batch);
        g_state.generatedTokenCount++;
        return env->NewStringUTF("</think>");
    }

    // think-start token — ID auto-detected from vocabulary at load time
    if (g_state.thinkStartToken != -1 && id == g_state.thinkStartToken) {
        g_state.isThinking = true;
    }

    llama_sampler_accept(currentSampler, id);

    // Decode token to text
    char buf[256];
    int len = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
    if (len < 0) len = 0;

    // Feed back to context
    llama_batch batch = llama_batch_get_one(&id, 1);
    if (llama_decode(g_state.ctx, batch) != 0) {
        LOGW("nativeNextToken: decode failed");
        return nullptr;
    }

    g_state.generatedTokenCount++;

    // Update t/s
    int64_t elapsed = llama_time_us() / 1000 - g_state.genStartTimeMs;
    if (elapsed > 0) {
        g_state.tokensPerSecond = g_state.generatedTokenCount * 1000.0f / elapsed;
    }

    return env->NewStringUTF(std::string(buf, len).c_str());
}

JNIEXPORT void JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeCancelGeneration(JNIEnv* env, jobject thiz) {
    LOGI("Cancelling generation");
    g_state.cancelled = true;
}

JNIEXPORT void JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeUnloadModel(JNIEnv* env, jobject thiz) {
    LOGI("Unloading model");
    g_state.cancelled = true;
    if (g_state.sampler) { llama_sampler_free(g_state.sampler); g_state.sampler = nullptr; }
    if (g_state.thinkSampler) { llama_sampler_free(g_state.thinkSampler); g_state.thinkSampler = nullptr; }
    if (g_state.ctx) { llama_free(g_state.ctx); g_state.ctx = nullptr; }
    if (g_state.model) { llama_model_free(g_state.model); g_state.model = nullptr; }
    g_state.tokens.clear();
    g_state.cancelled = false;
    g_state.isThinking = false;
    g_state.thinkStartToken = -1;
    g_state.thinkEndToken = -1;
    g_state.generatedTokenCount = 0;
    g_state.promptTokenCount = 0;
    g_state.tokensPerSecond = 0.0f;
}

JNIEXPORT jstring JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeGetModelName(JNIEnv* env, jobject thiz) {
    if (!g_state.model) return env->NewStringUTF("");
    char buf[256] = {0};
    llama_model_meta_val_str(g_state.model, "general.name", buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

JNIEXPORT jfloat JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeGetTokensPerSecond(JNIEnv* env, jobject thiz) {
    return g_state.tokensPerSecond;
}

JNIEXPORT jint JNICALL
Java_com_example_llama_internal_LlamaEngineImpl_nativeGetPromptTokenCount(JNIEnv* env, jobject thiz) {
    return g_state.promptTokenCount;
}

} // extern "C"
