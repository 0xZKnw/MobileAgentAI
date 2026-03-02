package com.example.llamaapp.engine

import com.example.llama.GenerationEvent

/**
 * Parses the raw token stream and emits GenerationEvents.
 * Handles <think>, </think> boundaries that may arrive mid-token.
 */
class ThinkTagParser {
    private var inThink = false
    private var buffer = StringBuilder()

    fun feed(rawToken: String): List<GenerationEvent> {
        val events = mutableListOf<GenerationEvent>()
        buffer.append(rawToken)
        val text = buffer.toString()

        when {
            text.contains("<think>") && !inThink -> {
                inThink = true
                events.add(GenerationEvent.ThinkStart)
                buffer.clear()
            }
            text.contains("</think>") && inThink -> {
                inThink = false
                val before = text.substringBefore("</think>")
                if (before.isNotEmpty()) events.add(GenerationEvent.ThinkToken(before))
                events.add(GenerationEvent.ThinkEnd)
                buffer.clear()
                val after = text.substringAfter("</think>")
                if (after.isNotEmpty()) buffer.append(after)
            }
            text.endsWith("<") || text.endsWith("</") || text.endsWith("</t") ||
            text.endsWith("</th") || text.endsWith("</thi") || text.endsWith("</thin") ||
            text.endsWith("</think") -> { /* hold partial tag */ }
            else -> {
                if (buffer.isNotEmpty()) {
                    if (inThink) events.add(GenerationEvent.ThinkToken(buffer.toString()))
                    else events.add(GenerationEvent.Token(buffer.toString()))
                    buffer.clear()
                }
            }
        }
        return events
    }

    fun reset() { inThink = false; buffer.clear() }
}
