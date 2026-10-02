package com.jarvis.assistant.core.memory

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GrammarAssistant(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_grammar", Context.MODE_PRIVATE)

    private val _isGrammarCorrectionEnabled = MutableStateFlow(
        prefs.getBoolean("grammar_enabled", false)
    )
    val isGrammarCorrectionEnabled: StateFlow<Boolean> = _isGrammarCorrectionEnabled.asStateFlow()

    fun setGrammarCorrectionEnabled(enabled: Boolean) {
        _isGrammarCorrectionEnabled.value = enabled
        prefs.edit().putBoolean("grammar_enabled", enabled).apply()
    }

    // Common spoken error patterns heuristic check
    fun checkGrammarMistake(spokenText: String): String? {
        if (!_isGrammarCorrectionEnabled.value) return null

        val lower = spokenText.lowercase().trim()
        return when {
            lower.contains("he go to") -> "Small English correction: 'He go to' should be 'He goes to'."
            lower.contains("she go to") -> "Small English correction: 'She go to' should be 'She goes to'."
            lower.contains("i didn't saw") -> "Small English correction: 'I didn't saw' should be 'I didn't see'."
            lower.contains("they is") -> "Small English correction: 'They is' should be 'They are'."
            else -> null
        }
    }
}
