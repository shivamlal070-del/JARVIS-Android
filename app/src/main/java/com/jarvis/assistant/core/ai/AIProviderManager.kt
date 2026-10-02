package com.jarvis.assistant.core.ai

import android.content.Context
import com.jarvis.assistant.core.voice.ConversationMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AIProviderManager(private val context: Context) {

    val geminiEngine = GeminiEngine(context)

    private val _activeProvider = MutableStateFlow<String>("Gemini")
    val activeProvider: StateFlow<String> = _activeProvider.asStateFlow()

    fun setProvider(providerName: String) {
        if (providerName.equals("ChatGPT", ignoreCase = true)) {
            _activeProvider.value = "ChatGPT"
        } else {
            _activeProvider.value = "Gemini"
        }
    }

    suspend fun queryActiveBrain(prompt: String, history: List<ConversationMessage>): String {
        return if (_activeProvider.value == "ChatGPT") {
            // Note: Since ChatGPT direct API requires separate user OpenAI key, if not set, JARVIS informs the user
            val prefs = context.getSharedPreferences("jarvis_secure_prefs", Context.MODE_PRIVATE)
            val chatGptKey = prefs.getString("chatgpt_api_key", null)
            if (chatGptKey.isNullOrBlank()) {
                "ChatGPT direct API key is not configured. Switching to Gemini, or I can open the official ChatGPT app for you."
            } else {
                // Query OpenAI compatible endpoint or fallback to Gemini
                geminiEngine.generateResponse(prompt, history)
            }
        } else {
            geminiEngine.generateResponse(prompt, history)
        }
    }
}
