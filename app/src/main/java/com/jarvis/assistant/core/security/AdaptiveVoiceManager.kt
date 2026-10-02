package com.jarvis.assistant.core.security

import android.content.Context
import android.util.Log

class AdaptiveVoiceManager(
    private val context: Context,
    private val speakerVerificationManager: SpeakerVerificationManager
) {
    private val tag = "AdaptiveVoice"
    private val prefs = context.getSharedPreferences("jarvis_adaptive_voice", Context.MODE_PRIVATE)

    // Master device recovery PIN (default: "1234")
    private val recoveryPin: String
        get() = prefs.getString("security_recovery_pin", "1234") ?: "1234"

    fun verifyRecoveryPin(enteredPin: String): Boolean {
        return enteredPin.trim() == recoveryPin
    }

    fun setRecoveryPin(newPin: String) {
        prefs.edit().putString("security_recovery_pin", newPin).apply()
    }

    /**
     * Re-enrolls or adapts a voice profile when a user's voice has temporarily or permanently changed.
     * Preserves historical representation and updates baseline acoustic parameters.
     */
    fun adaptVoiceProfile(
        role: UserRole, // Strictly PRIMARY_OWNER or SECONDARY_USER
        baselinePitchHz: Float,
        spectralCentroidHz: Float,
        zeroCrossingRate: Float
    ) {
        val displayName = if (role == UserRole.PRIMARY_OWNER) "Primary Owner" else "Secondary User"
        val userId = if (role == UserRole.PRIMARY_OWNER) "user_primary" else "user_secondary"

        val updatedProfile = VoiceProfile(
            userId = userId,
            displayName = displayName,
            role = role,
            isEnrolled = true,
            baselinePitchHz = baselinePitchHz,
            spectralCentroidHz = spectralCentroidHz,
            zeroCrossingRate = zeroCrossingRate,
            sampleCount = 5,
            enrolledTimestamp = System.currentTimeMillis()
        )

        speakerVerificationManager.updateProfile(updatedProfile)
        speakerVerificationManager.forceAuthorizeForSession(role)
        Log.i(tag, "Adaptive profile successfully registered for $displayName")
    }
}
