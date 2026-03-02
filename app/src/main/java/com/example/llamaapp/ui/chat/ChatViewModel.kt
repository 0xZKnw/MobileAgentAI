package com.example.llamaapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llama.GenerationEvent
import com.example.llama.InferenceStats
import com.example.llama.ModelConfig
import com.example.llama.ModelLoadState
import com.example.llamaapp.data.model.ChatMessage
import com.example.llamaapp.data.model.Conversation
import com.example.llamaapp.data.repository.ChatRepository
import com.example.llamaapp.domain.usecase.CancelGenerationUseCase
import com.example.llamaapp.domain.usecase.LoadModelUseCase
import com.example.llamaapp.domain.usecase.SendMessageUseCase
import com.example.llamaapp.domain.usecase.UnloadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val loadModelUseCase: LoadModelUseCase,
    private val cancelGenerationUseCase: CancelGenerationUseCase,
    private val unloadModelUseCase: UnloadModelUseCase,
    private val engine: com.example.llama.LlamaEngine
) : ViewModel() {

    // Mutable backing state
    private val _uiState = MutableStateFlow(ChatUiState())

    // Streaming token accumulator — separate from messages to avoid full LazyColumn recomposition
    val streamingText: MutableStateFlow<String> = MutableStateFlow("")

    // Think-block state
    val thinkingText: MutableStateFlow<String> = MutableStateFlow("")
    val isThinking: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // Forwarded from engine
    val loadState: StateFlow<ModelLoadState> = engine.loadState

    // Inference stats updated on Done event
    private val _inferenceStats = MutableStateFlow(InferenceStats())
    val inferenceStats: StateFlow<InferenceStats> = _inferenceStats.asStateFlow()

    // Active generation job — used internally for cancellation tracking
    private var generationJob: Job? = null

    // Conversations list from repository
    private val conversationsFlow = chatRepo.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Messages for the currently selected conversation, re-subscribed on conversationId change
    private val currentConversationIdFlow = MutableStateFlow<Long?>(null)

    private val messagesFlow: StateFlow<List<ChatMessage>> = currentConversationIdFlow
        .flatMapLatest { id ->
            if (id != null) chatRepo.getMessages(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Public read-only UI state composed from individual flows
    val uiState: StateFlow<ChatUiState> = _uiState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        // Keep uiState.conversations in sync
        viewModelScope.launch {
            conversationsFlow.collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }
        // Keep uiState.messages in sync
        viewModelScope.launch {
            messagesFlow.collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    // ── Conversation management ─────────────────────────────────────────────

    fun createConversation(modelId: Long? = null, systemPrompt: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = "Conversation ${System.currentTimeMillis()}"
            val id = chatRepo.createConversation(title, modelId, systemPrompt)
            selectConversation(id)
        }
    }

    fun selectConversation(id: Long) {
        currentConversationIdFlow.value = id
        _uiState.update { it.copy(currentConversationId = id, errorMessage = null) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepo.deleteConversation(id)
            // If we deleted the currently selected conversation, deselect it
            if (_uiState.value.currentConversationId == id) {
                currentConversationIdFlow.value = null
                _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
            }
        }
    }

    // ── Message sending ─────────────────────────────────────────────────────

    fun sendMessage(userText: String, config: ModelConfig = ModelConfig()) {
        val conversationId = _uiState.value.currentConversationId ?: return

        generationJob = viewModelScope.launch {
            // 1. Save user message on IO dispatcher
            launch(Dispatchers.IO) {
                chatRepo.saveMessage(
                    conversationId = conversationId,
                    role = "user",
                    content = userText,
                    thinkingContent = null,
                    generationTimeMs = null,
                    tokensPerSecond = null
                )
            }.join()

            // 2. Mark generating
            _uiState.update { it.copy(isGenerating = true, errorMessage = null) }

            // Reset streaming buffers
            streamingText.value = ""
            thinkingText.value = ""
            isThinking.value = false

            // 3. Collect generation events
            val history = _uiState.value.messages
            val startTimeMs = System.currentTimeMillis()

            sendMessageUseCase(conversationId, history, userText, config).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> streamingText.update { it + event.text }

                    is GenerationEvent.ThinkToken -> thinkingText.update { it + event.text }

                    is GenerationEvent.ThinkStart -> isThinking.value = true

                    is GenerationEvent.ThinkEnd -> isThinking.value = false

                    is GenerationEvent.Done -> {
                        val generationTimeMs = System.currentTimeMillis() - startTimeMs
                        val finalContent = streamingText.value
                        val finalThinking = thinkingText.value.takeIf { it.isNotEmpty() }

                        // 8. Save assistant message to Room on IO dispatcher
                        launch(Dispatchers.IO) {
                            chatRepo.saveMessage(
                                conversationId = conversationId,
                                role = "assistant",
                                content = finalContent,
                                thinkingContent = finalThinking,
                                generationTimeMs = generationTimeMs,
                                tokensPerSecond = event.tokensPerSecond
                            )
                        }

                        // Update inference stats
                        _inferenceStats.update {
                            it.copy(
                                tokensPerSecond = event.tokensPerSecond,
                                totalTokens = event.totalTokens
                            )
                        }

                        // Clear streaming state
                        streamingText.value = ""
                        thinkingText.value = ""
                        isThinking.value = false

                        _uiState.update { it.copy(isGenerating = false) }
                    }

                    is GenerationEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                errorMessage = event.message
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Model management ────────────────────────────────────────────────────

    fun loadModel(path: String, config: ModelConfig = ModelConfig()) {
        viewModelScope.launch {
            val result = loadModelUseCase(path, config)
            result.onFailure { throwable ->
                _uiState.update { it.copy(errorMessage = throwable.message) }
            }
        }
    }

    fun cancelGeneration() {
        cancelGenerationUseCase()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun unloadModel() {
        unloadModelUseCase()
    }

    // ── Error dismissal ─────────────────────────────────────────────────────

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
