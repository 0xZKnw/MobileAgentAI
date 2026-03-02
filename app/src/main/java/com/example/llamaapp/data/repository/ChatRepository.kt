package com.example.llamaapp.data.repository

import com.example.llamaapp.data.model.ChatMessage
import com.example.llamaapp.data.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun getConversations(): Flow<List<Conversation>>

    fun getMessages(conversationId: Long): Flow<List<ChatMessage>>

    suspend fun createConversation(
        title: String,
        modelId: Long?,
        systemPrompt: String?
    ): Long

    suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String,
        thinkingContent: String?,
        generationTimeMs: Long?,
        tokensPerSecond: Float?
    ): Long

    suspend fun deleteConversation(id: Long)

    suspend fun updateConversationTitle(id: Long, title: String)

    suspend fun pinConversation(id: Long, isPinned: Boolean)

    suspend fun getLastNMessages(conversationId: Long, n: Int): List<ChatMessage>
}
