package com.example.llamaapp.domain.usecase

import com.example.llama.LlamaEngine
import javax.inject.Inject

class UnloadModelUseCase @Inject constructor(
    private val engine: LlamaEngine
) {
    operator fun invoke() = engine.unloadModel()
}
