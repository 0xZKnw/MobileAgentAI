package com.example.llamaapp.ui.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.llama.LlamaEngine
import com.example.llama.ModelConfig
import com.example.llama.ModelLoadState
import com.example.llamaapp.data.model.GgufModel
import com.example.llamaapp.data.repository.ModelRepository
import com.example.llamaapp.domain.usecase.ImportModelUseCase
import com.example.llamaapp.domain.usecase.LoadModelUseCase
import com.example.llamaapp.worker.ModelDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelPickerUiState(
    val models: List<GgufModel> = emptyList(),
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val isLoading: Boolean = false,
    val loadedModelPath: String? = null,
    val errorMessage: String? = null,
    val downloadingModelId: String? = null,
    val downloadProgress: Float = 0f,
)

@HiltViewModel
class ModelPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val engine: LlamaEngine,
    private val importModelUseCase: ImportModelUseCase,
    private val loadModelUseCase: LoadModelUseCase,
) : ViewModel() {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    private val _uiState = MutableStateFlow(ModelPickerUiState())
    val uiState: StateFlow<ModelPickerUiState> = _uiState.asStateFlow()

    init {
        observeInstalledModels()
        observeEngineLoadState()
        observeDownloadProgress()
    }

    private fun observeInstalledModels() {
        viewModelScope.launch {
            modelRepo.getInstalledModels().collect { models ->
                _uiState.update { it.copy(models = models) }
            }
        }
    }

    private fun observeEngineLoadState() {
        viewModelScope.launch {
            engine.loadState.collect { loadState ->
                when (loadState) {
                    is ModelLoadState.Loaded -> {
                        val loadedPath = engine.getModelName()
                        _uiState.update { it.copy(loadedModelPath = loadedPath) }
                    }
                    is ModelLoadState.Idle -> {
                        _uiState.update { it.copy(loadedModelPath = null) }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("model_download")
                .collect { workInfos ->
                    val activeWork = workInfos.firstOrNull { info ->
                        !info.state.isFinished
                    }
                    if (activeWork != null) {
                        val progressInt = activeWork.progress.getInt(
                            ModelDownloadWorker.KEY_PROGRESS, 0
                        )
                        _uiState.update { state ->
                            state.copy(
                                downloadingModelId = activeWork.id.toString(),
                                downloadProgress = progressInt / 100f,
                            )
                        }
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                downloadingModelId = null,
                                downloadProgress = 0f,
                            )
                        }
                    }
                }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isImporting = true, importProgress = 0f) }
            val result = importModelUseCase(uri) { progress ->
                _uiState.update { it.copy(importProgress = progress) }
            }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isImporting = false, importProgress = 0f) }
                },
                onFailure = { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isImporting = false,
                            importProgress = 0f,
                            errorMessage = throwable.message ?: "Import failed",
                        )
                    }
                },
            )
        }
    }

    fun loadModel(model: GgufModel, config: ModelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val result = loadModelUseCase(model.filePath, config)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Failed to load model",
                        )
                    }
                },
            )
        }
    }

    fun deleteModel(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            modelRepo.deleteModel(id)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
