package com.example.llamaapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM models ORDER BY lastUsedAt DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE filePath = :path LIMIT 1")
    suspend fun getModelByPath(path: String): ModelEntity?

    @Query("SELECT * FROM models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: Long): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ModelEntity): Long

    @Delete
    suspend fun delete(entity: ModelEntity)

    @Query("UPDATE models SET lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateLastUsed(id: Long, lastUsedAt: Long)
}
