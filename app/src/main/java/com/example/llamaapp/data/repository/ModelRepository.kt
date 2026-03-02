package com.example.llamaapp.data.repository

import com.example.llamaapp.data.model.GgufModel
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ModelRepository {

    fun getInstalledModels(): Flow<List<GgufModel>>

    suspend fun registerModel(file: File): Long

    suspend fun getModelByPath(path: String): GgufModel?

    suspend fun deleteModel(id: Long)

    suspend fun updateLastUsed(id: Long)
}
