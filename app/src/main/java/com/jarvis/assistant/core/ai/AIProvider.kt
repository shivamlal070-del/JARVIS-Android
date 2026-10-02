package com.jarvis.assistant.core.ai

import com.jarvis.assistant.core.voice.ConversationMessage

interface AIProvider {
    val name: String

    suspend fun generateResponse(
        prompt: String,
        conversationHistory: List<ConversationMessage>,
        systemInstruction: String? = null
    ): String

    suspend fun analyzeImage(
        prompt: String,
        base64ImageData: String,
        mimeType: String = "image/jpeg"
    ): String
}
