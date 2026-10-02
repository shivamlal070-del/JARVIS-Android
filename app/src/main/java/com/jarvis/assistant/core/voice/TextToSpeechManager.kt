package com.jarvis.assistant.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {
    private val tag = "TextToSpeechManager"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtteranceCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(tag, "US English is not supported for TTS on this device.")
            } else {
                // JARVIS styling: slightly lower pitch, calm pacing
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(1.05f)
                isInitialized = true
                setupUtteranceListener()
            }
        } else {
            Log.e(tag, "TextToSpeech initialization failed with status $status")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(tag, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(tag, "TTS completed: $utteranceId")
                pendingUtteranceCallback?.invoke()
                pendingUtteranceCallback = null
            }

            override fun onError(utteranceId: String?) {
                Log.e(tag, "TTS error on utterance: $utteranceId")
                pendingUtteranceCallback?.invoke()
                pendingUtteranceCallback = null
            }
        })
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(tag, "TTS not ready yet; skipping voice playback.")
            onDone?.invoke()
            return
        }

        // Halts any ongoing speech for instant response
        stop()

        pendingUtteranceCallback = onDone
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        pendingUtteranceCallback = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
