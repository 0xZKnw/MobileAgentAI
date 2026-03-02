package com.example.llamaapp.domain.usecase

import com.example.llama.LlamaEngine
import com.example.llama.ModelConfig
import com.example.llamaapp.data.repository.ModelRepository
import javax.inject.Inject

class LoadModelUseCase @Inject constructor(
    private val engine: LlamaEngine,
    private val modelRepo: ModelRepository
) {
    suspend operator fun invoke(modelPath: String, config: ModelConfig): Result<Unit> {
        modelRepo.getModelByPath(modelPath)?.let { modelRepo.updateLastUsed(it.id) }
        return engine.loadModel(modelPath, config)
    }
}
