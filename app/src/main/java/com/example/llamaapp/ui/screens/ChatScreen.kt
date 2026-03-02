package com.example.llamaapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llama.ModelLoadState
import com.example.llamaapp.ui.chat.ChatViewModel
import com.example.llamaapp.ui.components.AssistantMessageBubble
import com.example.llamaapp.ui.components.ChatInputBar
import com.example.llamaapp.ui.components.PerformanceHUD
import com.example.llamaapp.ui.components.UserMessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToModelPicker: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val thinkingText by viewModel.thinkingText.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val inferenceStats by viewModel.inferenceStats.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    val messages = uiState.messages
    val isGenerating = uiState.isGenerating
    val isModelLoaded = loadState is ModelLoadState.Loaded

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by remember { mutableStateOf("") }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null) {
            snackbarHostState.showSnackbar(message = error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToModelPicker) {
                        Text(
                            text = "Load Model",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendMessage(text)
                        inputText = ""
                    }
                },
                onCancel = { viewModel.cancelGeneration() },
                isGenerating = isGenerating,
                isModelLoaded = isModelLoaded,
                modifier = Modifier.fillMaxWidth()
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Message list
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = messages,
                    key = { _, msg -> msg.id }
                ) { index, msg ->
                    val isLastMessage = index == messages.lastIndex
                    val showStreaming = isLastMessage && isGenerating && msg.role == "assistant"

                    when {
                        msg.role == "user" -> {
                            UserMessageBubble(
                                message = msg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                        showStreaming -> {
                            AssistantMessageBubble(
                                message = msg,
                                isStreaming = true,
                                streamingText = streamingText,
                                thinkingText = thinkingText,
                                isThinking = isThinking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                        else -> {
                            AssistantMessageBubble(
                                message = msg,
                                isStreaming = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // PerformanceHUD overlay — shown at top when stats are available
            if (inferenceStats.tokensPerSecond > 0f) {
                val modelName = (loadState as? ModelLoadState.Loaded)?.modelName
                PerformanceHUD(
                    stats = inferenceStats,
                    modelName = modelName,
                    isGenerating = isGenerating,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}
