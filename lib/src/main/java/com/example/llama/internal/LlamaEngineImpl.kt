package com.example.llama.internal

import android.content.Context
import com.example.llama.GenerationEvent
import com.example.llama.LlamaEngine
import com.example.llama.ModelConfig
import com.example.llama.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class LlamaEngineImpl(private val context: Context) : LlamaEngine {

    companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }

    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    // Keep these as standard JNI declarations for broad Android toolchain compatibility.
    external fun nativeInit(nativeLibDir: String)
    external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int, nBatch: Int): Boolean
    external fun nativePreparePrompt(formattedPrompt: String)
    external fun nativeNextToken(): String?
    external fun nativeCancelGeneration()
    external fun nativeUnloadModel()
    external fun nativeGetModelName(): String
    external fun nativeGetTokensPerSecond(): Float
    external fun nativeGetPromptTokenCount(): Int

    init {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        nativeInit(nativeLibDir)
    }

    override suspend fun loadModel(modelPath: String, config: ModelConfig): Result<Unit> {
        return withContext(llamaDispatcher) {
            _loadState.value = ModelLoadState.Loading(0f)
            runCatching {
                val ok = nativeLoadModel(modelPath, config.nThreads, config.nCtx, config.nBatch)
                if (!ok) throw IllegalStateException("Failed to load model: $modelPath")
                _loadState.value = ModelLoadState.Loaded(nativeGetModelName())
            }.onFailure {
                _loadState.value = ModelLoadState.Error(it.message ?: "Unknown error")
            }
        }
    }

    override fun generate(
        conversationHistory: List<Pair<String, String>>,
        userMessage: String,
        config: ModelConfig
    ): Flow<GenerationEvent> = generateRaw(buildSimplePrompt(conversationHistory, userMessage), config)

    /**
     * Primary generation entry point for ViewModel — accepts a fully-formatted Qwen3 prompt
     * (including <think> block if enabled). ViewModel in :app module calls this directly
     * after applying Qwen3ChatTemplate to avoid a cross-module import.
     *
     * Uses flow { while { nativeNextToken() } } polling — NOT callback-based flow.
     */
    fun generateRaw(formattedPrompt: String, config: ModelConfig): Flow<GenerationEvent> = flow {
        withContext(llamaDispatcher) { nativePreparePrompt(formattedPrompt) }
        val startTime = System.currentTimeMillis()
        var totalTokens = 0
        var isThinking = false

        while (true) {
            currentCoroutineContext().ensureActive()
            val token = withContext(llamaDispatcher) { nativeNextToken() } ?: break
            totalTokens++
            when {
                token == "<think>" -> {
                    isThinking = true
                    emit(GenerationEvent.ThinkStart)
                }
                token == "</think>" -> {
                    isThinking = false
                    emit(GenerationEvent.ThinkEnd)
                }
                isThinking -> emit(GenerationEvent.ThinkToken(token))
                else -> emit(GenerationEvent.Token(token))
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val tps = if (elapsed > 0) totalTokens * 1000f / elapsed else 0f
        emit(GenerationEvent.Done(tps, totalTokens))
    }.flowOn(llamaDispatcher)

    override fun cancelGeneration() {
        nativeCancelGeneration()
    }

    override fun unloadModel() {
        runBlocking(llamaDispatcher) { nativeUnloadModel() }
        _loadState.value = ModelLoadState.Idle
    }

    override fun isModelLoaded(): Boolean = loadState.value is ModelLoadState.Loaded

    override fun getModelName(): String? = (loadState.value as? ModelLoadState.Loaded)?.modelName

    /**
     * Minimal inline ChatML/Qwen3 prompt formatter used when calling generate() directly
     * (e.g. tests, fallback). The ViewModel should call generateRaw() with a
     * Qwen3ChatTemplate-formatted prompt instead.
     */
    private fun buildSimplePrompt(
        history: List<Pair<String, String>>,
        userMessage: String
    ): String = buildString {
        append("<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n")
        for ((user, assistant) in history) {
            append("<|im_start|>user\n$user<|im_end|>\n")
            append("<|im_start|>assistant\n$assistant<|im_end|>\n")
        }
        append("<|im_start|>user\n$userMessage<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }
}
