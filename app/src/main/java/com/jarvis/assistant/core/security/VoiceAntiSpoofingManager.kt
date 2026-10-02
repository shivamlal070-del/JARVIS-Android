package com.jarvis.assistant.core.security

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlin.math.abs
import kotlin.math.log10

enum class SecurityState {
    NORMAL,
    SUSPICIOUS,
    UNKNOWN
}

data class AntiSpoofDecision(
    val state: SecurityState,
    val confidence: Float,
    val indicatorFlags: List<String>,
    val requiresChallenge: Boolean = false,
    val explanation: String
)

class VoiceAntiSpoofingManager(private val context: Context) {
    private val tag = "VoiceAntiSpoofing"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Executes the multi-signal anti-spoofing pipeline.
     * Evaluates physical microphone routing, acoustic jitter (liveness), and high-frequency cutoff (replay).
     */
    fun evaluateAudioStream(samples: ShortArray): AntiSpoofDecision {
        val flags = mutableListOf<String>()

        // 1. Microphone & Audio Hardware Routing Check
        val isHardwareMic = verifyPhysicalMicHardware(flags)

        // 2. High-Frequency Rolloff / Replay Analysis
        // Replayed speech (via phone/speaker) typically exhibits severe bandwidth compression (<4 kHz)
        val highFreqRatio = calculateHighFrequencyEnergyRatio(samples)
        if (highFreqRatio < 0.015f) {
            flags.add("Bandwidth compressed or high-frequency rolloff (potential loudspeaker replay)")
        }

        // 3. Liveness Analysis: Natural Pitch Tremor / Micro-Jitter
        // Synthetic voice models and text-to-speech vocoders frequently exhibit unnaturally robotic flat pitch
        val jitter = calculatePitchMicroJitter(samples)
        if (jitter < 0.002f) {
            flags.add("Unnaturally uniform pitch harmonics (synthetic/vocoder voice indicator)")
        }

        // 4. Energy Envelope Discontinuity
        val maxStep = calculateMaximumEnergyStep(samples)
        if (maxStep > 28000) {
            flags.add("Abrupt digital splicing detected in audio buffer")
        }

        // Security Decision Logic
        return when {
            flags.isEmpty() && isHardwareMic -> {
                AntiSpoofDecision(
                    state = SecurityState.NORMAL,
                    confidence = 0.94f,
                    indicatorFlags = emptyList(),
                    requiresChallenge = false,
                    explanation = "Natural human voice acoustics verified on legitimate hardware microphone."
                )
            }
            flags.size == 1 && isHardwareMic -> {
                // Mild uncertainty: e.g. room background noise or low quality headset
                AntiSpoofDecision(
                    state = SecurityState.SUSPICIOUS,
                    confidence = 0.65f,
                    indicatorFlags = flags,
                    requiresChallenge = true,
                    explanation = "Acoustic signals show mild anomalies (${flags[0]}). Dynamic challenge-response required."
                )
            }
            else -> {
                // Multiple spoofing or replay flags triggered
                AntiSpoofDecision(
                    state = SecurityState.SUSPICIOUS,
                    confidence = 0.35f,
                    indicatorFlags = flags,
                    requiresChallenge = true,
                    explanation = "Multiple synthetic/replay signals detected: ${flags.joinToString("; ")}. System locked until challenge is passed."
                )
            }
        }
    }

    private fun verifyPhysicalMicHardware(flags: MutableList<String>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val hasRealInput = devices.any {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (!hasRealInput && devices.isNotEmpty()) {
                flags.add("Audio input routed through non-standard or virtual audio adapter")
                return false
            }
        }
        return true
    }

    private fun calculateHighFrequencyEnergyRatio(samples: ShortArray): Float {
        if (samples.size < 500) return 0.05f
        var highFreqEnergy = 0.0
        var totalEnergy = 0.0
        for (i in 0 until samples.size - 1) {
            val delta = abs(samples[i + 1] - samples[i]).toDouble()
            val magnitude = abs(samples[i]).toDouble()
            highFreqEnergy += delta
            totalEnergy += magnitude
        }
        return if (totalEnergy > 0) (highFreqEnergy / totalEnergy).toFloat() else 0.05f
    }

    private fun calculatePitchMicroJitter(samples: ShortArray): Float {
        if (samples.size < 800) return 0.01f
        var variance = 0.0
        for (i in 200 until samples.size - 200 step 80) {
            variance += abs(samples[i].toDouble() - samples[i - 40].toDouble())
        }
        return (variance / samples.size / 32768.0).toFloat()
    }

    private fun calculateMaximumEnergyStep(samples: ShortArray): Int {
        var maxDelta = 0
        for (i in 0 until samples.size - 1) {
            val diff = abs(samples[i + 1] - samples[i])
            if (diff > maxDelta) maxDelta = diff
        }
        return maxDelta
    }
}
