package com.example.llamaapp.data.repository

import com.example.llamaapp.data.db.ConversationDao
import com.example.llamaapp.data.db.ConversationEntity
import com.example.llamaapp.data.db.MessageDao
import com.example.llamaapp.data.db.MessageEntity
import com.example.llamaapp.data.model.ChatMessage
import com.example.llamaapp.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// ── Mapper extension functions ────────────────────────────────────────────────

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    modelId = modelId,
    systemPrompt = systemPrompt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    totalTokens = totalTokens,
    isPinned = isPinned
)

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    thinkingContent = thinkingContent,
    createdAt = createdAt,
    generationTimeMs = generationTimeMs,
    tokensPerSecond = tokensPerSecond
)

// ── Implementation ────────────────────────────────────────────────────────────

class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun getConversations(): Flow<List<Conversation>> =
        conversationDao.getAllConversations().map { list -> list.map { it.toDomain() } }

    override fun getMessages(conversationId: Long): Flow<List<ChatMessage>> =
        messageDao.getMessagesForConversation(conversationId).map { list -> list.map { it.toDomain() } }

    override suspend fun createConversation(
        title: String,
        modelId: Long?,
        systemPrompt: String?
    ): Long {
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(
            id = 0L,
            title = title,
            modelId = modelId,
            systemPrompt = systemPrompt,
            createdAt = now,
            updatedAt = now,
            totalTokens = 0,
            isPinned = false
        )
        return conversationDao.insert(entity)
    }

    override suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String,
        thinkingContent: String?,
        generationTimeMs: Long?,
        tokensPerSecond: Float?
    ): Long {
        val entity = MessageEntity(
            id = 0L,
            conversationId = conversationId,
            role = role,
            content = content,
            thinkingContent = thinkingContent,
            createdAt = System.currentTimeMillis(),
            generationTimeMs = generationTimeMs,
            tokensPerSecond = tokensPerSecond
        )
        val messageId = messageDao.insert(entity)
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())
        return messageId
    }

    override suspend fun deleteConversation(id: Long) {
        messageDao.deleteByConversationId(id)
        conversationDao.deleteById(id)
    }

    override suspend fun updateConversationTitle(id: Long, title: String) {
        conversationDao.updateTimestamp(id, System.currentTimeMillis())
        val entity = conversationDao.getConversationById(id) ?: return
        conversationDao.update(entity.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun pinConversation(id: Long, isPinned: Boolean) {
        conversationDao.updatePinned(id, isPinned)
    }

    override suspend fun getLastNMessages(conversationId: Long, n: Int): List<ChatMessage> =
        messageDao.getLastNMessages(conversationId, n).map { it.toDomain() }
}
