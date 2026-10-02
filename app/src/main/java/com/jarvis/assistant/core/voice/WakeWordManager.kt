package com.jarvis.assistant.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordManager(private val context: Context) {
    private val tag = "WakeWordManager"

    var onWakeWordDetected: (() -> Unit)? = null
    var onMicConflictDetected: (() -> Unit)? = null

    private val isListening = AtomicBoolean(false)
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Audio recording parameters for battery-efficient lightweight audio buffer inspection
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

    private var audioRecord: AudioRecord? = null

    // Energy threshold & simple voice activity detection parameters
    private val voiceEnergyThreshold = 1800.0

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isListening.get()) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed. Microphone might be occupied by another app.")
                onMicConflictDetected?.invoke()
                return
            }

            audioRecord?.startRecording()
            isListening.set(true)

            listeningJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                var consecutiveSpeechFrames = 0

                while (isListening.get() && isActive) {
                    val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readSamples > 0) {
                        // Compute RMS energy
                        var sum = 0.0
                        for (i in 0 until readSamples) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = Math.sqrt(sum / readSamples)

                        if (rms > voiceEnergyThreshold) {
                            consecutiveSpeechFrames++
                            // When sustained voice energy matching a spoken phrase is detected:
                            if (consecutiveSpeechFrames >= 4) {
                                consecutiveSpeechFrames = 0
                                // Pause local audio reading and trigger wake-word verification
                                withContext(Dispatchers.Main) {
                                    onWakeWordDetected?.invoke()
                                }
                                break
                            }
                        } else {
                            if (consecutiveSpeechFrames > 0) consecutiveSpeechFrames--
                        }
                    } else if (readSamples == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.w(tag, "AudioRecord read error: Another application likely took the microphone.")
                        withContext(Dispatchers.Main) {
                            onMicConflictDetected?.invoke()
                        }
                        break
                    }
                    delay(50) // Battery conservation sleep loop
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Audio recording permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(tag, "Error starting wake word detection: ${e.message}")
            onMicConflictDetected?.invoke()
        }
    }

    fun stopListening() {
        isListening.set(false)
        listeningJob?.cancel()
        listeningJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord: ${e.message}")
        }
    }
}
