package com.example.llamaapp.domain.usecase

import com.example.llama.GenerationEvent
import com.example.llama.LlamaEngine
import com.example.llama.ModelConfig
import com.example.llamaapp.data.model.ChatMessage
import com.example.llamaapp.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val engine: LlamaEngine,
    private val chatRepo: ChatRepository
) {
    operator fun invoke(
        conversationId: Long,
        history: List<ChatMessage>,
        userMessage: String,
        config: ModelConfig
    ): Flow<GenerationEvent> {
        // Build (user, assistant) pairs for context window
        val historyPairs = history
            .filter { it.role != "system" }
            .zipWithNext { a, b ->
                if (a.role == "user" && b.role == "assistant") {
                    Pair(a.content, b.content)
                } else null
            }
            .filterNotNull()
        return engine.generate(historyPairs, userMessage, config)
    }
}
