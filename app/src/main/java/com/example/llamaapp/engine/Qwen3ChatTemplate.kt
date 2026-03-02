package com.example.llamaapp.engine

class Qwen3ChatTemplate {
    companion object {
        private const val IM_START = "<|im_start|>"
        private const val IM_END = "<|im_end|>"
        private const val DEFAULT_SYSTEM = "You are a helpful assistant."
    }

    fun format(
        history: List<Pair<String, String>>,
        userMessage: String,
        enableThinking: Boolean,
        systemPrompt: String = DEFAULT_SYSTEM
    ): String = buildString {
        append(IM_START).append("system\n").append(systemPrompt).append(IM_END).append("\n")
        for ((user, assistant) in history) {
            append(IM_START).append("user\n").append(user).append(IM_END).append("\n")
            append(IM_START).append("assistant\n").append(assistant).append(IM_END).append("\n")
        }
        append(IM_START).append("user\n").append(userMessage).append(IM_END).append("\n")
        append(IM_START).append("assistant\n")
        if (enableThinking) append("<think>\n") else append("\n\n")
    }
}
