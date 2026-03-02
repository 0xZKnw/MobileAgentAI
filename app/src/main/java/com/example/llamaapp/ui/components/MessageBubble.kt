package com.example.llamaapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.llamaapp.data.model.ChatMessage
import com.example.llamaapp.ui.theme.appColors

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatTime(timestampMs: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    return "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}

// ---------------------------------------------------------------------------
// UserMessageBubble
// ---------------------------------------------------------------------------

@Composable
fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.appColors.userBubble,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    // No userBubbleText token in AppColors — use a high-contrast white
                    color = Color(0xFFE8E8F0)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE8E8F0).copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// AssistantMessageBubble
// ---------------------------------------------------------------------------

@Composable
fun AssistantMessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    streamingText: String = "",
    thinkingText: String = "",
    isThinking: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.appColors.aiBubble,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {

                // Thinking block
                if (isStreaming) {
                    if (thinkingText.isNotBlank() || isThinking) {
                        ThinkingBlock(
                            thinkingContent = thinkingText,
                            isStreaming = isThinking
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    val thinking = message.thinkingContent
                    if (!thinking.isNullOrBlank()) {
                        ThinkingBlock(
                            thinkingContent = thinking,
                            isStreaming = false
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Main content
                val displayText = if (isStreaming) streamingText else message.content
                if (isStreaming && displayText.isBlank()) {
                    // Pulsing "thinking" indicator — no tokens yet
                    PulsingDotsIndicator()
                } else {
                    MarkdownRenderer(
                        text = displayText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Footer row: perf stats + timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tokens-per-second stat (only on completed messages)
                    if (!isStreaming && message.tokensPerSecond != null) {
                        Text(
                            text = "%.1f t/s".format(message.tokensPerSecond),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(1.dp))
                    }

                    // Timestamp (only on completed messages)
                    if (!isStreaming) {
                        Text(
                            text = formatTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PulsingDotsIndicator — used when streaming but no tokens yet
// ---------------------------------------------------------------------------

@Composable
private fun PulsingDotsIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots_pulse")

    @Composable
    fun dot(delayMs: Int): Float {
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = delayMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_alpha_$delayMs"
        )
        return alpha
    }

    val a1 = dot(0)
    val a2 = dot(200)
    val a3 = dot(400)

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { idx ->
            val alpha = when (idx) { 0 -> a1; 1 -> a2; else -> a3 }
            Surface(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(alpha),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ) {}
        }
    }
}

// ---------------------------------------------------------------------------
// ChatInputBar
// ---------------------------------------------------------------------------

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    val isSendEnabled = value.isNotBlank() && isModelLoaded && !isGenerating

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = {
                        Text(
                            text = "Message...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (isSendEnabled) onSend() }
                    ),
                    singleLine = false,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Cancel button — only when generating
                if (isGenerating) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Send button
                IconButton(
                    onClick = { if (isSendEnabled) onSend() },
                    enabled = isSendEnabled,
                    modifier = Modifier.padding(start = if (isGenerating) 0.dp else 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send message",
                        tint = if (isSendEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
                    )
                }
            }

            // "No model loaded" hint
            if (!isModelLoaded) {
                Text(
                    text = "No model loaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier
                        .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}
