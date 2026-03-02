package com.example.llamaapp.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "models",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val fileSize: Long,
    val paramCount: String,
    val quantization: String,
    val addedAt: Long,
    val lastUsedAt: Long
)
