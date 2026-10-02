package com.jarvis.assistant.core.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ActiveSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val activeSubject: String? = null,
    val activeTopic: String? = null,
    val lastQuestion: String? = null
)

class SessionManager(private val context: Context) {
    private val _currentSession = MutableStateFlow(ActiveSession())
    val currentSession: StateFlow<ActiveSession> = _currentSession.asStateFlow()

    fun updateActiveProblem(subject: String, question: String) {
        val current = _currentSession.value
        _currentSession.value = current.copy(
            activeSubject = subject,
            lastQuestion = question
        )
    }

    fun clearActiveProblem() {
        val current = _currentSession.value
        _currentSession.value = current.copy(
            activeSubject = null,
            lastQuestion = null
        )
    }
}
