package com.jarvis.assistant.core.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionManager(private val context: Context) {
    private val tag = "SpeechRecognition"

    var onSpeechChunk: ((String) -> Unit)? = null
    var onError: ((message: String, isMicConflict: Boolean) -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isContinuousListeningActive = false

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(tag, "Microphone ready for speech.")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(tag, "User speech started.")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        onRmsChanged?.invoke(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(tag, "User paused or speech segment ended.")
                    }

                    override fun onError(error: Int) {
                        val isConflict = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                                          error == SpeechRecognizer.ERROR_AUDIO)

                        // In continuous mode, silence or speech timeouts are normal thinking pauses!
                        if (isContinuousListeningActive && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                                             error == SpeechRecognizer.ERROR_NO_MATCH)) {
                            Log.d(tag, "Thinking pause detected (timeout $error). Seamlessly restarting recognizer.")
                            restartListeningSafely()
                            return
                        }

                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Another app may be using the microphone."
                            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy with another application."
                            else -> "Speech recognition event: $error"
                        }

                        onError?.invoke(errorMsg, isConflict)

                        if (isContinuousListeningActive && !isConflict) {
                            restartListeningSafely()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedChunk = matches[0]
                            Log.d(tag, "Recognized segment: \"$recognizedChunk\"")
                            onSpeechChunk?.invoke(recognizedChunk)
                        }

                        // Continue listening across pauses if continuous session is active
                        if (isContinuousListeningActive) {
                            restartListeningSafely()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0]
                            onSpeechChunk?.invoke(partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            Log.e(tag, "SpeechRecognizer is not available on this device.")
        }
    }

    fun startContinuousListening() {
        isContinuousListeningActive = true
        startRecognizerInternal()
    }

    fun stopContinuousListening() {
        isContinuousListeningActive = false
        stopListening()
    }

    private fun restartListeningSafely() {
        if (!isContinuousListeningActive) return
        try {
            speechRecognizer?.cancel()
            startRecognizerInternal()
        } catch (e: Exception) {
            Log.w(tag, "Error restarting recognizer: ${e.message}")
        }
    }

    private fun startRecognizerInternal() {
        // Route audio through Bluetooth headset if connected
        if (audioManager.isBluetoothScoAvailableOffCall && !audioManager.isBluetoothScoOn) {
            try {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            } catch (e: Exception) {
                Log.w(tag, "Bluetooth SCO activation error: ${e.message}")
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Request long speech timeout where supported
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 15000L)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 10000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError?.invoke("Could not start speech recognition: ${e.message}", false)
        }
    }

    private fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        } catch (e: Exception) {
            Log.w(tag, "Error stopping SpeechRecognizer: ${e.message}")
        }
    }

    fun destroy() {
        isContinuousListeningActive = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
