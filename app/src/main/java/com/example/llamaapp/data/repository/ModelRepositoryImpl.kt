package com.example.llamaapp.data.repository

import com.example.llamaapp.data.db.ModelDao
import com.example.llamaapp.data.db.ModelEntity
import com.example.llamaapp.data.model.GgufModel
import com.example.llamaapp.storage.ModelStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

// ── Mapper extension functions ────────────────────────────────────────────────

fun ModelEntity.toDomain(): GgufModel = GgufModel(
    id = id,
    name = name,
    filePath = filePath,
    fileSize = fileSize,
    paramCount = paramCount,
    quantization = quantization,
    addedAt = addedAt,
    lastUsedAt = lastUsedAt
)

// ── Filename parsing helpers ──────────────────────────────────────────────────

private fun parseParamCount(name: String): String =
    Regex("(\\d+(?:\\.\\d+)?[bBmMkK])").find(name)?.value?.uppercase() ?: "?"

private fun parseQuantization(name: String): String =
    Regex("(q[0-9]+_[a-z0-9_]+)", RegexOption.IGNORE_CASE).find(name)?.value?.uppercase() ?: "?"

// ── Implementation ────────────────────────────────────────────────────────────

class ModelRepositoryImpl @Inject constructor(
    private val modelDao: ModelDao,
    private val storage: ModelStorageManager
) : ModelRepository {

    override fun getInstalledModels(): Flow<List<GgufModel>> =
        modelDao.getAllModels().map { list -> list.map { it.toDomain() } }

    override suspend fun registerModel(file: File): Long {
        val name = file.name
        val entity = ModelEntity(
            id = 0L,
            name = name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            paramCount = parseParamCount(name),
            quantization = parseQuantization(name),
            addedAt = System.currentTimeMillis(),
            lastUsedAt = 0L
        )
        return modelDao.insert(entity)
    }

    override suspend fun getModelByPath(path: String): GgufModel? =
        modelDao.getModelByPath(path)?.toDomain()

    override suspend fun deleteModel(id: Long) {
        // Find the entity by iterating all models to locate by id,
        // since ModelDao exposes getModelByPath but not getModelById.
        // We collect synchronously inside a coroutine context via a snapshot.
        // Use a dedicated helper that fetches the current list from Flow first emission.
        val entity = modelDao.getModelById(id) ?: return
        storage.deleteModel(File(entity.filePath))
        modelDao.delete(entity)
    }

    override suspend fun updateLastUsed(id: Long) {
        modelDao.updateLastUsed(id, System.currentTimeMillis())
    }
}
