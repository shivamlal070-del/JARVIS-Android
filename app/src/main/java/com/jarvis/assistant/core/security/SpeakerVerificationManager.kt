package com.jarvis.assistant.core.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

enum class UserRole {
    PRIMARY_OWNER,
    SECONDARY_USER,
    UNKNOWN
}

data class VoiceProfile(
    val userId: String,
    val displayName: String,
    val role: UserRole,
    val isEnrolled: Boolean = true,
    val baselinePitchHz: Float,       // Fundamental frequency F0
    val spectralCentroidHz: Float,   // Frequency brightness
    val zeroCrossingRate: Float,     // High-frequency noise vs vocal cord vibration
    val sampleCount: Int = 1,
    val enrolledTimestamp: Long = System.currentTimeMillis()
)

sealed class SpeakerVerificationResult {
    data class Authenticated(val role: UserRole, val displayName: String, val confidence: Float) : SpeakerVerificationResult()
    data class Uncertain(val closestRole: UserRole, val confidence: Float, val reason: String) : SpeakerVerificationResult()
    object UnknownSpeaker : SpeakerVerificationResult()
    object UnenrolledSystem : SpeakerVerificationResult()
}

class SpeakerVerificationManager(private val context: Context) {
    private val tag = "SpeakerVerification"
    private val prefs = context.getSharedPreferences("jarvis_speaker_profiles", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserRole>(UserRole.UNKNOWN)
    val currentUser: StateFlow<UserRole> = _currentUser.asStateFlow()

    private val _currentUserName = MutableStateFlow<String>("Locked (Unknown)")
    val currentUserName: StateFlow<String> = _currentUserName.asStateFlow()

    private val _isAuthenticated = MutableStateFlow<Boolean>(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    // Enrolled voice profiles for the 2 authorized users
    private var primaryProfile: VoiceProfile? = null
    private var secondaryProfile: VoiceProfile? = null

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val primJson = prefs.getString("profile_primary", null)
        if (primJson != null) {
            primaryProfile = deserializeProfile(primJson)
        } else {
            // Default baseline profile for Primary Owner (e.g. adult male/female range)
            primaryProfile = VoiceProfile(
                userId = "user_primary",
                displayName = "Primary Owner",
                role = UserRole.PRIMARY_OWNER,
                baselinePitchHz = 125.0f,
                spectralCentroidHz = 1650.0f,
                zeroCrossingRate = 0.085f,
                sampleCount = 3
            )
            saveProfile(primaryProfile!!)
        }

        val secJson = prefs.getString("profile_secondary", null)
        if (secJson != null) {
            secondaryProfile = deserializeProfile(secJson)
        } else {
            // Default baseline profile for Secondary User
            secondaryProfile = VoiceProfile(
                userId = "user_secondary",
                displayName = "Secondary User",
                role = UserRole.SECONDARY_USER,
                baselinePitchHz = 210.0f,
                spectralCentroidHz = 2200.0f,
                zeroCrossingRate = 0.110f,
                sampleCount = 3
            )
            saveProfile(secondaryProfile!!)
        }
    }

    /**
     * Extracts acoustic features from a raw PCM audio buffer and compares with enrolled profiles.
     */
    fun verifySpeaker(audioSamples: ShortArray): SpeakerVerificationResult {
        if (primaryProfile == null && secondaryProfile == null) {
            return SpeakerVerificationResult.UnenrolledSystem
        }

        val features = extractAcousticFeatures(audioSamples)
        Log.d(tag, "Extracted Features: Pitch=${features.pitchHz}Hz, Centroid=${features.centroidHz}Hz, ZCR=${features.zcr}")

        // Compute similarity to Primary Owner
        val scorePrimary = computeSimilarity(features, primaryProfile!!)
        // Compute similarity to Secondary User
        val scoreSecondary = computeSimilarity(features, secondaryProfile!!)

        Log.d(tag, "Match Scores: Primary=$scorePrimary, Secondary=$scoreSecondary")

        val bestScore: Float
        val bestProfile: VoiceProfile
        if (scorePrimary >= scoreSecondary) {
            bestScore = scorePrimary
            bestProfile = primaryProfile!!
        } else {
            bestScore = scoreSecondary
            bestProfile = secondaryProfile!!
        }

        // Strict security thresholds
        return when {
            bestScore >= 0.72f -> {
                _currentUser.value = bestProfile.role
                _currentUserName.value = bestProfile.displayName
                _isAuthenticated.value = true
                SpeakerVerificationResult.Authenticated(bestProfile.role, bestProfile.displayName, bestScore)
            }
            bestScore >= 0.48f -> {
                // Potential user whose voice changed due to cold/fatigue or noisy environment
                SpeakerVerificationResult.Uncertain(
                    bestProfile.role,
                    bestScore,
                    "Voice similarity ($bestScore) is below strict threshold. Adaptive verification required."
                )
            }
            else -> {
                // Unknown speaker: lock system
                lockSystem()
                SpeakerVerificationResult.UnknownSpeaker
            }
        }
    }

    fun lockSystem() {
        _currentUser.value = UserRole.UNKNOWN
        _currentUserName.value = "Locked (Unknown Speaker)"
        _isAuthenticated.value = false
    }

    fun forceAuthorizeForSession(role: UserRole) {
        _currentUser.value = role
        _currentUserName.value = if (role == UserRole.PRIMARY_OWNER) primaryProfile?.displayName ?: "Primary Owner" else secondaryProfile?.displayName ?: "Secondary User"
        _isAuthenticated.value = true
    }

    fun updateProfile(profile: VoiceProfile) {
        if (profile.role == UserRole.PRIMARY_OWNER) {
            primaryProfile = profile
            saveProfile(profile)
        } else if (profile.role == UserRole.SECONDARY_USER) {
            secondaryProfile = profile
            saveProfile(profile)
        }
    }

    private data class ExtractedFeatures(
        val pitchHz: Float,
        val centroidHz: Float,
        val zcr: Float
    )

    private fun extractAcousticFeatures(samples: ShortArray): ExtractedFeatures {
        if (samples.isEmpty()) return ExtractedFeatures(140f, 1800f, 0.09f)

        // 1. Zero-Crossing Rate
        var zeroCrossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        val zcr = zeroCrossings.toFloat() / samples.size

        // 2. Fundamental Frequency (Pitch F0) estimation using Autocorrelation
        val sampleRate = 16000
        val minLag = sampleRate / 400 // 400 Hz max pitch
        val maxLag = sampleRate / 70  // 70 Hz min pitch
        var bestCorrelation = 0.0
        var bestLag = 0

        for (lag in minLag..maxLag) {
            var sum = 0.0
            val len = samples.size - lag
            for (i in 0 until len step 2) {
                sum += samples[i].toDouble() * samples[i + lag].toDouble()
            }
            if (sum > bestCorrelation) {
                bestCorrelation = sum
                bestLag = lag
            }
        }
        val pitchHz = if (bestLag > 0) (sampleRate.toFloat() / bestLag) else 140f

        // 3. Spectral Centroid approximation
        var weightedSum = 0.0
        var totalEnergy = 0.0
        for (i in 0 until samples.size - 1) {
            val freqApprox = abs(samples[i + 1] - samples[i]).toDouble()
            weightedSum += freqApprox * (i.toDouble() / samples.size * 8000)
            totalEnergy += freqApprox
        }
        val centroidHz = if (totalEnergy > 0) (weightedSum / totalEnergy).toFloat() else 1800f

        return ExtractedFeatures(pitchHz, centroidHz, zcr)
    }

    private fun computeSimilarity(feat: ExtractedFeatures, profile: VoiceProfile): Float {
        // Normalized Euclidean distance across dimensions
        val pitchDiff = abs(feat.pitchHz - profile.baselinePitchHz) / 100.0f
        val centroidDiff = abs(feat.centroidHz - profile.spectralCentroidHz) / 1500.0f
        val zcrDiff = abs(feat.zcr - profile.zeroCrossingRate) / 0.1f

        val dist = sqrt(pitchDiff * pitchDiff * 0.5 + centroidDiff * centroidDiff * 0.3 + zcrDiff * zcrDiff * 0.2)
        // Convert distance to similarity [0, 1]
        val similarity = (1.0f / (1.0f + dist)).toFloat()
        return similarity.coerceIn(0.0f, 1.0f)
    }

    private fun saveProfile(profile: VoiceProfile) {
        val key = if (profile.role == UserRole.PRIMARY_OWNER) "profile_primary" else "profile_secondary"
        val json = JSONObject().apply {
            put("userId", profile.userId)
            put("displayName", profile.displayName)
            put("role", profile.role.name)
            put("isEnrolled", profile.isEnrolled)
            put("baselinePitchHz", profile.baselinePitchHz.toDouble())
            put("spectralCentroidHz", profile.spectralCentroidHz.toDouble())
            put("zeroCrossingRate", profile.zeroCrossingRate.toDouble())
            put("sampleCount", profile.sampleCount)
            put("enrolledTimestamp", profile.enrolledTimestamp)
        }
        prefs.edit().putString(key, json.toString()).apply()
    }

    private fun deserializeProfile(jsonStr: String): VoiceProfile {
        val obj = JSONObject(jsonStr)
        return VoiceProfile(
            userId = obj.getString("userId"),
            displayName = obj.getString("displayName"),
            role = UserRole.valueOf(obj.getString("role")),
            isEnrolled = obj.getBoolean("isEnrolled"),
            baselinePitchHz = obj.getDouble("baselinePitchHz").toFloat(),
            spectralCentroidHz = obj.getDouble("spectralCentroidHz").toFloat(),
            zeroCrossingRate = obj.getDouble("zeroCrossingRate").toFloat(),
            sampleCount = obj.getInt("sampleCount"),
            enrolledTimestamp = obj.getLong("enrolledTimestamp")
        )
    }
}
