package com.example.llamaapp.domain.usecase

import android.net.Uri
import com.example.llamaapp.data.model.GgufModel
import com.example.llamaapp.data.repository.ModelRepository
import com.example.llamaapp.storage.ModelStorageManager
import javax.inject.Inject

class ImportModelUseCase @Inject constructor(
    private val storageManager: ModelStorageManager,
    private val modelRepo: ModelRepository
) {
    suspend operator fun invoke(
        uri: Uri,
        onProgress: (Float) -> Unit = {}
    ): Result<GgufModel> = runCatching {
        val file = storageManager.importModel(uri, onProgress)
        modelRepo.registerModel(file)
        modelRepo.getModelByPath(file.absolutePath)
            ?: error("Model not found after import: ${file.absolutePath}")
    }
}
