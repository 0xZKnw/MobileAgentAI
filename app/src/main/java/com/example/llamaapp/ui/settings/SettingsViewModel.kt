package com.example.llamaapp.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.llama.ModelConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val nThreads: Int = 4,
    val nCtx: Int = 4096,
    val nBatch: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.8f,
    val topK: Int = 20,
    val repeatPenalty: Float = 1.1f,
    val enableThinking: Boolean = true,
    val thinkingTemperature: Float = 0.6f,
    val thinkingTopP: Float = 0.95f,
    val maxNewTokens: Int = 2048,
    val isSaving: Boolean = false,
    val savedMessage: String? = null
)

private object PreferencesKeys {
    val N_THREADS = intPreferencesKey("n_threads")
    val N_CTX = intPreferencesKey("n_ctx")
    val N_BATCH = intPreferencesKey("n_batch")
    val TEMPERATURE = floatPreferencesKey("temperature")
    val TOP_P = floatPreferencesKey("top_p")
    val TOP_K = intPreferencesKey("top_k")
    val REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
    val ENABLE_THINKING = booleanPreferencesKey("enable_thinking")
    val THINKING_TEMPERATURE = floatPreferencesKey("thinking_temperature")
    val THINKING_TOP_P = floatPreferencesKey("thinking_top_p")
    val MAX_NEW_TOKENS = intPreferencesKey("max_new_tokens")
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    val currentModelConfig: StateFlow<ModelConfig> = _uiState
        .map { state ->
            ModelConfig(
                nThreads = state.nThreads,
                nCtx = state.nCtx,
                nBatch = state.nBatch,
                temperature = state.temperature,
                topP = state.topP,
                topK = state.topK,
                repeatPenalty = state.repeatPenalty,
                enableThinking = state.enableThinking,
                thinkingTemperature = state.thinkingTemperature,
                thinkingTopP = state.thinkingTopP,
                maxNewTokens = state.maxNewTokens
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelConfig())

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _uiState.update { current ->
                    current.copy(
                        nThreads = prefs[PreferencesKeys.N_THREADS] ?: 4,
                        nCtx = prefs[PreferencesKeys.N_CTX] ?: 4096,
                        nBatch = prefs[PreferencesKeys.N_BATCH] ?: 512,
                        temperature = prefs[PreferencesKeys.TEMPERATURE] ?: 0.7f,
                        topP = prefs[PreferencesKeys.TOP_P] ?: 0.8f,
                        topK = prefs[PreferencesKeys.TOP_K] ?: 20,
                        repeatPenalty = prefs[PreferencesKeys.REPEAT_PENALTY] ?: 1.1f,
                        enableThinking = prefs[PreferencesKeys.ENABLE_THINKING] ?: true,
                        thinkingTemperature = prefs[PreferencesKeys.THINKING_TEMPERATURE] ?: 0.6f,
                        thinkingTopP = prefs[PreferencesKeys.THINKING_TOP_P] ?: 0.95f,
                        maxNewTokens = prefs[PreferencesKeys.MAX_NEW_TOKENS] ?: 2048
                    )
                }
            }
        }
    }

    fun saveSettings(state: SettingsUiState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.N_THREADS] = state.nThreads
                prefs[PreferencesKeys.N_CTX] = state.nCtx
                prefs[PreferencesKeys.N_BATCH] = state.nBatch
                prefs[PreferencesKeys.TEMPERATURE] = state.temperature
                prefs[PreferencesKeys.TOP_P] = state.topP
                prefs[PreferencesKeys.TOP_K] = state.topK
                prefs[PreferencesKeys.REPEAT_PENALTY] = state.repeatPenalty
                prefs[PreferencesKeys.ENABLE_THINKING] = state.enableThinking
                prefs[PreferencesKeys.THINKING_TEMPERATURE] = state.thinkingTemperature
                prefs[PreferencesKeys.THINKING_TOP_P] = state.thinkingTopP
                prefs[PreferencesKeys.MAX_NEW_TOKENS] = state.maxNewTokens
            }
            _uiState.update { it.copy(isSaving = false, savedMessage = "Settings saved") }
            delay(2000)
            _uiState.update { it.copy(savedMessage = null) }
        }
    }

    fun resetToDefaults() {
        val defaults = SettingsUiState()
        _uiState.update { defaults }
        saveSettings(defaults)
    }

    fun updateNThreads(value: Int) = _uiState.update { it.copy(nThreads = value) }
    fun updateNCtx(value: Int) = _uiState.update { it.copy(nCtx = value) }
    fun updateNBatch(value: Int) = _uiState.update { it.copy(nBatch = value) }
    fun updateTemperature(value: Float) = _uiState.update { it.copy(temperature = value) }
    fun updateTopP(value: Float) = _uiState.update { it.copy(topP = value) }
    fun updateTopK(value: Int) = _uiState.update { it.copy(topK = value) }
    fun updateRepeatPenalty(value: Float) = _uiState.update { it.copy(repeatPenalty = value) }
    fun updateEnableThinking(value: Boolean) = _uiState.update { it.copy(enableThinking = value) }
    fun updateThinkingTemperature(value: Float) = _uiState.update { it.copy(thinkingTemperature = value) }
    fun updateThinkingTopP(value: Float) = _uiState.update { it.copy(thinkingTopP = value) }
    fun updateMaxNewTokens(value: Int) = _uiState.update { it.copy(maxNewTokens = value) }
}
