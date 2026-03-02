package com.example.llama

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Sealed UI state for loading lifecycle
sealed class ModelLoadState {
    object Idle : ModelLoadState()
    data class Loading(val progress: Float) : ModelLoadState()
    data class Loaded(val modelName: String) : ModelLoadState()
    data class Error(val message: String) : ModelLoadState()
}

// Token generation state emitted from Flow
sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data class ThinkToken(val text: String) : GenerationEvent()
    object ThinkStart : GenerationEvent()
    object ThinkEnd : GenerationEvent()
    data class Done(val tokensPerSecond: Float, val totalTokens: Int) : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
}

// Performance stats
data class InferenceStats(
    val tokensPerSecond: Float = 0f,
    val timeToFirstToken: Long = 0L,
    val totalTokens: Int = 0,
    val promptTokens: Int = 0
)

// Model config (persisted via DataStore)
data class ModelConfig(
    val nThreads: Int = 4,
    val nCtx: Int = 4096,
    val nBatch: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val topK: Int = 20,
    val repeatPenalty: Float = 1.1f,
    val enableThinking: Boolean = true,
    val thinkingTemperature: Float = 0.6f,
    val thinkingTopP: Float = 0.95f,
    val maxNewTokens: Int = 2048
)

// LlamaEngine public interface
interface LlamaEngine {
    val loadState: StateFlow<ModelLoadState>
    suspend fun loadModel(modelPath: String, config: ModelConfig): Result<Unit>
    fun generate(conversationHistory: List<Pair<String, String>>, userMessage: String, config: ModelConfig): Flow<GenerationEvent>
    fun cancelGeneration()
    fun unloadModel()
    fun isModelLoaded(): Boolean
    fun getModelName(): String?
}
