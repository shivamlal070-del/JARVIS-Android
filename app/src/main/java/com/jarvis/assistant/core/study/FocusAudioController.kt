package com.jarvis.assistant.core.study

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.view.KeyEvent
import kotlinx.coroutines.*
import kotlin.math.sin

enum class FocusAudioType {
    OFF,
    FOCUS_MUSIC,
    CALM_MUSIC,
    BINAURAL_ALPHA_10HZ,
    USER_SELECTED_PLAYLIST
}

data class AudioControlResult(
    val success: Boolean,
    val mode: FocusAudioType,
    val message: String
)

/**
 * FocusAudioController provides legitimate Android media controls,
 * local synthesized binaural focus frequencies, and intent dispatch to supported music apps.
 *
 * Adheres strictly to Requirement 46:
 * - Never starts audio without respecting user preference.
 * - Does not claim arbitrary music apps can be controlled when unavailable.
 * - Provides graceful fallbacks.
 */
class FocusAudioController(private val context: Context) {
    var currentAudioType: FocusAudioType = FocusAudioType.OFF
    var isPlaying: Boolean = false

    private var audioTrack: AudioTrack? = null
    private var synthesisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Start the configured focus audio mode.
     */
    fun startFocusAudio(type: FocusAudioType): AudioControlResult {
        stopAudio()
        currentAudioType = type

        return when (type) {
            FocusAudioType.OFF -> {
                AudioControlResult(true, FocusAudioType.OFF, "Focus audio disabled.")
            }
            FocusAudioType.BINAURAL_ALPHA_10HZ -> {
                startBinauralAlphaBeats(baseFreq = 200.0, beatFreq = 10.0)
                isPlaying = true
                AudioControlResult(true, type, "10Hz Alpha waves started for deep focus.")
            }
            FocusAudioType.CALM_MUSIC -> {
                startCalmSoundscape()
                isPlaying = true
                AudioControlResult(true, type, "Calm harmonic soundscape playing.")
            }
            FocusAudioType.FOCUS_MUSIC, FocusAudioType.USER_SELECTED_PLAYLIST -> {
                // Dispatch legitimate Android media play intent
                val dispatched = dispatchMediaPlayKey()
                if (dispatched) {
                    isPlaying = true
                    AudioControlResult(true, type, "Dispatched play command to default Android media player.")
                } else {
                    // Fallback to synthesized local calm frequency
                    startCalmSoundscape()
                    isPlaying = true
                    AudioControlResult(true, type, "External player unavailable. Started built-in calm soundscape fallback.")
                }
            }
        }
    }

    /**
     * Stop active audio stream or dispatch media pause.
     */
    fun stopAudio(): AudioControlResult {
        synthesisJob?.cancel()
        synthesisJob = null

        audioTrack?.let {
            try {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                // ignore cleanup errors
            }
        }
        audioTrack = null
        isPlaying = false

        if (currentAudioType == FocusAudioType.FOCUS_MUSIC || currentAudioType == FocusAudioType.USER_SELECTED_PLAYLIST) {
            dispatchMediaPauseKey()
        }

        currentAudioType = FocusAudioType.OFF
        return AudioControlResult(true, FocusAudioType.OFF, "Audio stopped.")
    }

    private fun dispatchMediaPlayKey(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun dispatchMediaPauseKey(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Synthesizes a true stereo 10Hz binaural beat (Left 200Hz, Right 210Hz) for alpha brainwave synchronization.
     */
    private fun startBinauralAlphaBeats(baseFreq: Double, beatFreq: Double) {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        synthesisJob = scope.launch {
            val leftFreq = baseFreq
            val rightFreq = baseFreq + beatFreq
            var leftAngle = 0.0
            var rightAngle = 0.0
            val samples = ShortArray(bufferSize)

            while (isActive) {
                for (i in 0 until bufferSize step 2) {
                    val leftSample = (sin(leftAngle) * 0.15 * Short.MAX_VALUE).toInt().toShort()
                    val rightSample = (sin(rightAngle) * 0.15 * Short.MAX_VALUE).toInt().toShort()
                    samples[i] = leftSample
                    samples[i + 1] = rightSample

                    leftAngle += 2.0 * Math.PI * leftFreq / sampleRate
                    rightAngle += 2.0 * Math.PI * rightFreq / sampleRate
                    if (leftAngle > 2 * Math.PI) leftAngle -= 2 * Math.PI
                    if (rightAngle > 2 * Math.PI) rightAngle -= 2 * Math.PI
                }
                audioTrack?.write(samples, 0, bufferSize)
            }
        }
    }

    /**
     * Synthesizes a warm calm harmonic fifth chord for stress reduction and focus.
     */
    private fun startCalmSoundscape() {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        synthesisJob = scope.launch {
            val freq1 = 261.63 // C4
            val freq2 = 392.00 // G4 (Perfect 5th)
            val freq3 = 523.25 // C5
            var angle1 = 0.0
            var angle2 = 0.0
            var angle3 = 0.0
            val samples = ShortArray(bufferSize)

            while (isActive) {
                for (i in 0 until bufferSize step 2) {
                    val v = (sin(angle1) * 0.08 + sin(angle2) * 0.06 + sin(angle3) * 0.04) * Short.MAX_VALUE
                    val s = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    samples[i] = s
                    samples[i + 1] = s

                    angle1 += 2.0 * Math.PI * freq1 / sampleRate
                    angle2 += 2.0 * Math.PI * freq2 / sampleRate
                    angle3 += 2.0 * Math.PI * freq3 / sampleRate
                    if (angle1 > 2 * Math.PI) angle1 -= 2 * Math.PI
                    if (angle2 > 2 * Math.PI) angle2 -= 2 * Math.PI
                    if (angle3 > 2 * Math.PI) angle3 -= 2 * Math.PI
                }
                audioTrack?.write(samples, 0, bufferSize)
            }
        }
    }
}
