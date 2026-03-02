package com.example.llamaapp.domain.usecase

import com.example.llama.LlamaEngine
import javax.inject.Inject

class CancelGenerationUseCase @Inject constructor(
    private val engine: LlamaEngine
) {
    operator fun invoke() = engine.cancelGeneration()
}
