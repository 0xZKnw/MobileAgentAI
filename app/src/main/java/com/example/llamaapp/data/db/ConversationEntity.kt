package com.example.llamaapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val modelId: Long? = null,
    val systemPrompt: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val totalTokens: Int = 0,
    val isPinned: Boolean = false
)
