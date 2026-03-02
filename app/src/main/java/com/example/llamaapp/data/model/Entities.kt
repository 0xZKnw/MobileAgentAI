package com.example.llamaapp.data.model

data class GgufModel(
    val id: Long,
    val name: String,
    val filePath: String,
    val fileSize: Long,
    val paramCount: String,
    val quantization: String,
    val addedAt: Long,
    val lastUsedAt: Long
)

data class ChatMessage(
    val id: Long,
    val conversationId: Long,
    val role: String,
    val content: String,
    val thinkingContent: String?,
    val createdAt: Long,
    val generationTimeMs: Long?,
    val tokensPerSecond: Float?
)

data class Conversation(
    val id: Long,
    val title: String,
    val modelId: Long?,
    val systemPrompt: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val totalTokens: Int,
    val isPinned: Boolean
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }
