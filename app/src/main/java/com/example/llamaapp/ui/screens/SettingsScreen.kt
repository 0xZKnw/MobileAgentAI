package com.example.llamaapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llamaapp.ui.settings.SettingsUiState
import com.example.llamaapp.ui.settings.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedMessage) {
        val message = uiState.savedMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PerformanceSection(uiState = uiState, viewModel = viewModel)
            Spacer(modifier = Modifier.height(8.dp))
            SamplingSection(uiState = uiState, viewModel = viewModel)
            Spacer(modifier = Modifier.height(8.dp))
            ThinkingSection(uiState = uiState, viewModel = viewModel)
            Spacer(modifier = Modifier.height(24.dp))
            ActionButtons(
                isSaving = uiState.isSaving,
                onSave = { viewModel.saveSettings(uiState) },
                onReset = { viewModel.resetToDefaults() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PerformanceSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SectionHeader(title = "Performance")

    SliderSetting(
        label = "Threads (nThreads)",
        value = uiState.nThreads.toFloat(),
        onValueChange = { viewModel.updateNThreads(it.roundToInt()) },
        valueRange = 1f..12f,
        displayValue = "${uiState.nThreads}",
        steps = 10
    )

    SliderSetting(
        label = "Context Size (nCtx)",
        value = uiState.nCtx.toFloat(),
        onValueChange = { viewModel.updateNCtx(it.roundToInt()) },
        valueRange = 512f..8192f,
        displayValue = "${uiState.nCtx}",
        steps = 14
    )

    SliderSetting(
        label = "Batch Size (nBatch)",
        value = uiState.nBatch.toFloat(),
        onValueChange = { viewModel.updateNBatch(it.roundToInt()) },
        valueRange = 64f..1024f,
        displayValue = "${uiState.nBatch}",
        steps = 14
    )
}

@Composable
private fun SamplingSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SectionHeader(title = "Sampling")

    SliderSetting(
        label = "Temperature",
        value = uiState.temperature,
        onValueChange = { viewModel.updateTemperature(it) },
        valueRange = 0f..2f,
        displayValue = "%.2f".format(uiState.temperature)
    )

    SliderSetting(
        label = "Top-P",
        value = uiState.topP,
        onValueChange = { viewModel.updateTopP(it) },
        valueRange = 0f..1f,
        displayValue = "%.2f".format(uiState.topP)
    )

    SliderSetting(
        label = "Top-K",
        value = uiState.topK.toFloat(),
        onValueChange = { viewModel.updateTopK(it.roundToInt()) },
        valueRange = 1f..100f,
        displayValue = "${uiState.topK}",
        steps = 98
    )

    SliderSetting(
        label = "Repeat Penalty",
        value = uiState.repeatPenalty,
        onValueChange = { viewModel.updateRepeatPenalty(it) },
        valueRange = 1f..1.5f,
        displayValue = "%.2f".format(uiState.repeatPenalty)
    )

    SliderSetting(
        label = "Max New Tokens",
        value = uiState.maxNewTokens.toFloat(),
        onValueChange = { viewModel.updateMaxNewTokens(it.roundToInt()) },
        valueRange = 256f..4096f,
        displayValue = "${uiState.maxNewTokens}",
        steps = 14
    )
}

@Composable
private fun ThinkingSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SectionHeader(title = "Thinking")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Enable Thinking Mode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = uiState.enableThinking,
            onCheckedChange = { viewModel.updateEnableThinking(it) }
        )
    }

    SliderSetting(
        label = "Thinking Temperature",
        value = uiState.thinkingTemperature,
        onValueChange = { viewModel.updateThinkingTemperature(it) },
        valueRange = 0f..1f,
        displayValue = "%.2f".format(uiState.thinkingTemperature)
    )

    SliderSetting(
        label = "Thinking Top-P",
        value = uiState.thinkingTopP,
        onValueChange = { viewModel.updateThinkingTopP(it) },
        valueRange = 0f..1f,
        displayValue = "%.2f".format(uiState.thinkingTopP)
    )
}

@Composable
private fun ActionButtons(
    isSaving: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isSaving) "Saving…" else "Save Settings")
        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Reset to Defaults")
        }
    }
}
