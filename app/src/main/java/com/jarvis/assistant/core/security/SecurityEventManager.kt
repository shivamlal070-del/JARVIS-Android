package com.jarvis.assistant.core.security

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class SecurityEventType {
    WAKE_DETECTED,
    SPEAKER_AUTHENTICATED,
    SPEAKER_REJECTED_UNKNOWN,
    SUSPICIOUS_VOICE_SPOOF_RISK,
    CHALLENGE_REQUESTED,
    CHALLENGE_PASSED,
    CHALLENGE_FAILED,
    ADAPTIVE_PROFILE_UPDATED,
    SENSITIVE_ACTION_BLOCKED,
    CROSS_DEVICE_AUTH_FAILURE
}

data class SecurityEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: SecurityEventType,
    val summary: String,
    val confidenceScore: Float? = null,
    val deviceSource: String = "Samsung Galaxy Tab S5e"
)

class SecurityEventManager(private val context: Context) {

    private val _recentEvents = MutableStateFlow<List<SecurityEvent>>(
        listOf(
            SecurityEvent(
                eventType = SecurityEventType.SPEAKER_AUTHENTICATED,
                summary = "System initialized with 2 enrolled speaker profiles (Primary & Secondary).",
                confidenceScore = 1.0f
            )
        )
    )
    val recentEvents: StateFlow<List<SecurityEvent>> = _recentEvents.asStateFlow()

    fun logEvent(
        eventType: SecurityEventType,
        summary: String,
        confidence: Float? = null
    ) {
        val event = SecurityEvent(
            eventType = eventType,
            summary = summary,
            confidenceScore = confidence
        )
        val updated = (_recentEvents.value + event).takeLast(50)
        _recentEvents.value = updated
    }

    fun clearEvents() {
        _recentEvents.value = emptyList()
    }
}
