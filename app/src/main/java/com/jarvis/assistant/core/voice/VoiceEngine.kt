package com.jarvis.assistant.core.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.core.ai.AIProviderManager
import com.jarvis.assistant.core.ai.CommandInterpreter
import com.jarvis.assistant.core.device.AndroidActionManager
import com.jarvis.assistant.core.device.BluetoothAudioDeviceManager
import com.jarvis.assistant.core.guard.JarvisGuard
import com.jarvis.assistant.core.security.*
import com.jarvis.assistant.core.study.StudyModeManager
import com.jarvis.assistant.core.sync.*
import com.jarvis.assistant.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * State machine stages for the JARVIS conversational turn lifecycle.
 */
enum class JarvisVoiceState {
    IDLE,
    WAKE_DETECTED,
    AUTHENTICATING,
    CONVERSATION_LISTENING,
    END_PHRASE_DETECTED,
    PROCESSING,
    SPEAKING
}

typealias VoiceState = JarvisVoiceState

data class ConversationMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "USER" or "JARVIS"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionDetail: String? = null
)

/**
 * VoiceEngine — Native Android Foreground Service for hands-free conversational operation.
 * Implements [SpeechRecognizer] with continuous listening and a custom state machine
 * for handling wake phrases ("Hello Jarvis") and end phrases ("Jarvis, it is enough").
 */
class VoiceEngine() : Service(), RecognitionListener {

    private val tag = "VoiceEngine"
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Injected or dynamically resolved dependencies
    private var injectedContext: Context? = null
    private var injectedAiProviderManager: AIProviderManager? = null
    private var injectedActionManager: AndroidActionManager? = null
    private var injectedStudyModeManager: StudyModeManager? = null
    private var injectedJarvisGuard: JarvisGuard? = null
    private var injectedActiveDeviceManager: ActiveDeviceManager? = null
    private var injectedCrossDeviceSyncManager: CrossDeviceSyncManager? = null
    private var injectedSpeakerVerificationManager: SpeakerVerificationManager? = null
    private var injectedVoiceAntiSpoofingManager: VoiceAntiSpoofingManager? = null
    private var injectedChallengeResponseManager: ChallengeResponseManager? = null
    private var injectedSecurityEventManager: SecurityEventManager? = null
    private var injectedBluetoothAudioDeviceManager: BluetoothAudioDeviceManager? = null

    // Safe getters for runtime dependencies
    private val appContext: Context
        get() = injectedContext ?: applicationContext ?: JarvisApp.instance

    val aiProviderManager: AIProviderManager
        get() = injectedAiProviderManager ?: JarvisApp.instance.aiProviderManager

    val actionManager: AndroidActionManager
        get() = injectedActionManager ?: JarvisApp.instance.actionManager

    val studyModeManager: StudyModeManager
        get() = injectedStudyModeManager ?: JarvisApp.instance.studyModeManager

    val jarvisGuard: JarvisGuard
        get() = injectedJarvisGuard ?: JarvisApp.instance.jarvisGuard

    val activeDeviceManager: ActiveDeviceManager
        get() = injectedActiveDeviceManager ?: JarvisApp.instance.activeDeviceManager

    val crossDeviceSyncManager: CrossDeviceSyncManager
        get() = injectedCrossDeviceSyncManager ?: JarvisApp.instance.crossDeviceSyncManager

    val speakerVerificationManager: SpeakerVerificationManager
        get() = injectedSpeakerVerificationManager ?: JarvisApp.instance.speakerVerificationManager

    val voiceAntiSpoofingManager: VoiceAntiSpoofingManager
        get() = injectedVoiceAntiSpoofingManager ?: JarvisApp.instance.voiceAntiSpoofingManager

    val challengeResponseManager: ChallengeResponseManager
        get() = injectedChallengeResponseManager ?: JarvisApp.instance.challengeResponseManager

    val securityEventManager: SecurityEventManager
        get() = injectedSecurityEventManager ?: JarvisApp.instance.securityEventManager

    val bluetoothAudioDeviceManager: BluetoothAudioDeviceManager
        get() = injectedBluetoothAudioDeviceManager ?: JarvisApp.instance.bluetoothAudioDeviceManager

    // Persistent Voice Preferences
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("jarvis_voice_settings", Context.MODE_PRIVATE)
    }

    // Audio routing
    private val audioManager by lazy {
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Sub-engines
    lateinit var ttsManager: TextToSpeechManager
        private set
    lateinit var wakeWordManager: WakeWordManager
        private set
    lateinit var commandInterpreter: CommandInterpreter
        private set

    // Native Speech Recognizer for Continuous Listening
    private var speechRecognizer: SpeechRecognizer? = null
    private var isContinuousListeningActive = false

    // State machine StateFlows
    private val _voiceState = MutableStateFlow(JarvisVoiceState.IDLE)
    val voiceState: StateFlow<JarvisVoiceState> = _voiceState.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory.asStateFlow()

    private val _currentTask = MutableStateFlow("Waiting for Hello Jarvis")
    val currentTask: StateFlow<String> = _currentTask.asStateFlow()

    private val _micActive = MutableStateFlow(false)
    val micActive: StateFlow<Boolean> = _micActive.asStateFlow()

    private val _isHandsFreeEnabled = MutableStateFlow(false)
    val isHandsFreeEnabled: StateFlow<Boolean> = _isHandsFreeEnabled.asStateFlow()

    // Turn text accumulator buffer
    private val _accumulatedTurnBuffer = MutableStateFlow("")
    val accumulatedTurnBuffer: StateFlow<String> = _accumulatedTurnBuffer.asStateFlow()

    // Real-time microphone RMS audio level for UI HUD ripple
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    // Active Challenge during security verification
    private var pendingChallenge: VoiceChallenge? = null

    // Watchdog timer job for conversation turns
    private var conversationTimeoutJob: Job? = null
    private var lastDiscussedTarget: String? = null
    private var isServiceForeground = false

    // Configurable Wake & End Phrases
    var wakePhrase: String
        get() = prefs.getString("wake_phrase", "Hello Jarvis") ?: "Hello Jarvis"
        set(value) = prefs.edit().putString("wake_phrase", value).apply()

    var endPhrase: String
        get() = prefs.getString("end_phrase", "Jarvis, it is enough") ?: "Jarvis, it is enough"
        set(value) = prefs.edit().putString("end_phrase", value).apply()

    var sayListeningAck: Boolean
        get() = prefs.getBoolean("say_listening_ack", true)
        set(value) = prefs.edit().putBoolean("say_listening_ack", value).apply()

    var maxConversationSeconds: Int
        get() = prefs.getInt("max_conversation_seconds", 300)
        set(value) = prefs.edit().putInt("max_conversation_seconds", value).apply()

    // Secondary Constructor for dependency-injected instantiation
    constructor(
        context: Context,
        aiProviderManager: AIProviderManager? = null,
        actionManager: AndroidActionManager? = null,
        studyModeManager: StudyModeManager? = null,
        jarvisGuard: JarvisGuard? = null,
        activeDeviceManager: ActiveDeviceManager? = null,
        crossDeviceSyncManager: CrossDeviceSyncManager? = null,
        speakerVerificationManager: SpeakerVerificationManager? = null,
        voiceAntiSpoofingManager: VoiceAntiSpoofingManager? = null,
        challengeResponseManager: ChallengeResponseManager? = null,
        securityEventManager: SecurityEventManager? = null,
        bluetoothAudioDeviceManager: BluetoothAudioDeviceManager? = null
    ) : this() {
        this.injectedContext = context
        this.injectedAiProviderManager = aiProviderManager
        this.injectedActionManager = actionManager
        this.injectedStudyModeManager = studyModeManager
        this.injectedJarvisGuard = jarvisGuard
        this.injectedActiveDeviceManager = activeDeviceManager
        this.injectedCrossDeviceSyncManager = crossDeviceSyncManager
        this.injectedSpeakerVerificationManager = speakerVerificationManager
        this.injectedVoiceAntiSpoofingManager = voiceAntiSpoofingManager
        this.injectedChallengeResponseManager = challengeResponseManager
        this.injectedSecurityEventManager = securityEventManager
        this.injectedBluetoothAudioDeviceManager = bluetoothAudioDeviceManager

        initEngineInternal()
    }

    inner class LocalBinder : Binder() {
        fun getService(): VoiceEngine = this@VoiceEngine
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        initEngineInternal()
        Log.i(tag, "VoiceEngine Foreground Service created.")
    }

    /**
     * Internal initializer invoked upon creation (service or secondary constructor).
     */
    private fun initEngineInternal() {
        instance = this
        ttsManager = TextToSpeechManager(appContext)
        wakeWordManager = WakeWordManager(appContext)
        commandInterpreter = CommandInterpreter(appContext, aiProviderManager)

        initSpeechRecognizer()
        setupListeners()
    }

    /**
     * Initializes the native Android SpeechRecognizer.
     */
    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(this@VoiceEngine)
                }
                Log.d(tag, "SpeechRecognizer initialized successfully.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize SpeechRecognizer: ${e.message}")
            }
        } else {
            Log.e(tag, "SpeechRecognizer is not available on this device system.")
        }
    }

    private fun setupListeners() {
        // Wake Word Detection Callback
        wakeWordManager.onWakeWordDetected = {
            scope.launch {
                Log.d(tag, "Wake phrase detected: $wakePhrase")
                handleWakeWordActivated()
            }
        }

        wakeWordManager.onMicConflictDetected = {
            scope.launch {
                Log.w(tag, "Microphone conflict in wake word detector.")
                _currentTask.value = "Microphone in use by another application."
            }
        }
    }

    // ==========================================
    // Foreground Service Lifecycle & Actions
    // ==========================================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_START_HANDS_FREE -> {
                startForegroundNotification()
                startHandsFree()
            }
            ACTION_STOP, ACTION_STOP_HANDS_FREE -> {
                stopHandsFree()
                stopForegroundNotification()
                stopSelf()
            }
            ACTION_START_MANUAL_INTERACTION -> {
                startForegroundNotification()
                startManualVoiceInteraction()
            }
            ACTION_END_TURN -> {
                manualEndTurn()
            }
            ACTION_EMERGENCY_STOP -> {
                emergencyStop()
            }
            null -> {
                startForegroundNotification()
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (!isServiceForeground) {
            val notification = buildForegroundNotification(
                title = "JARVIS Voice Engine",
                content = "Listening for \"$wakePhrase\"..."
            )
            try {
                startForeground(NOTIFICATION_ID, notification)
                isServiceForeground = true
            } catch (e: Exception) {
                Log.w(tag, "Could not start foreground notification: ${e.message}")
            }
        }
    }

    private fun stopForegroundNotification() {
        if (isServiceForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.w(tag, "Error stopping foreground: ${e.message}")
            }
            isServiceForeground = false
        }
    }

    private fun updateForegroundNotification(status: String) {
        if (!isServiceForeground) return
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notification = buildForegroundNotification(
            title = "JARVIS Voice Engine",
            content = status
        )
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        ensureNotificationChannel()

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            appContext,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(appContext, VoiceEngine::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            appContext,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(appContext, JarvisApp.CHANNEL_ID_VOICE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Listening", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                JarvisApp.CHANNEL_ID_VOICE,
                "JARVIS Active Listening Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing hands-free wake-word detection status."
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    // ==========================================
    // Public Control Interface
    // ==========================================

    fun startHandsFree() {
        _isHandsFreeEnabled.value = true
        startForegroundNotification()
        startWakeWordDetection()
    }

    fun stopHandsFree() {
        _isHandsFreeEnabled.value = false
        emergencyStop()
    }

    fun emergencyStop() {
        conversationTimeoutJob?.cancel()
        conversationTimeoutJob = null
        wakeWordManager.stopListening()
        stopContinuousListening()
        ttsManager.stop()
        pendingChallenge = null
        _micActive.value = false
        _accumulatedTurnBuffer.value = ""
        _voiceState.value = JarvisVoiceState.IDLE
        _currentTask.value = "Waiting for $wakePhrase"
        updateForegroundNotification("Waiting for $wakePhrase")
    }

    fun startManualVoiceInteraction() {
        ttsManager.stop()
        scope.launch {
            handleWakeWordActivated()
        }
    }

    fun manualEndTurn() {
        scope.launch {
            val text = _accumulatedTurnBuffer.value
            if (text.isNotBlank()) {
                handleEndPhraseDetected(text)
            } else {
                emergencyStop()
            }
        }
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        _voiceState.value = JarvisVoiceState.SPEAKING
        _currentTask.value = "JARVIS is responding..."
        updateForegroundNotification("Speaking: \"${text.take(40)}...\"")
        ttsManager.speak(text) {
            onFinished?.invoke()
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
        _voiceState.value = JarvisVoiceState.IDLE
    }

    fun clearHistory() {
        _conversationHistory.value = emptyList()
    }

    // ==========================================
    // Continuous Listening Implementation
    // ==========================================

    private fun startContinuousListening() {
        isContinuousListeningActive = true
        _micActive.value = true
        startRecognizerInternal()
    }

    private fun stopContinuousListening() {
        isContinuousListeningActive = false
        _micActive.value = false
        stopListeningInternal()
    }

    private fun restartListeningSafely() {
        if (!isContinuousListeningActive) return
        try {
            speechRecognizer?.cancel()
            startRecognizerInternal()
        } catch (e: Exception) {
            Log.w(tag, "Error restarting SpeechRecognizer: ${e.message}")
        }
    }

    private fun startRecognizerInternal() {
        // Route audio through Bluetooth headset if available
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
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Long speech timeouts for uninterrupted continuous listening
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 15000L)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 10000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Could not start SpeechRecognizer: ${e.message}")
            handleRecognitionError("Could not start speech recognition: ${e.message}", false)
        }
    }

    private fun stopListeningInternal() {
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

    // ==========================================
    // RecognitionListener Implementation
    // ==========================================

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(tag, "SpeechRecognizer: Ready for speech.")
    }

    override fun onBeginningOfSpeech() {
        Log.d(tag, "SpeechRecognizer: User speech started.")
    }

    override fun onRmsChanged(rmsdB: Float) {
        _rmsLevel.value = rmsdB
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(tag, "SpeechRecognizer: End of speech segment.")
    }

    override fun onError(error: Int) {
        val isConflict = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                          error == SpeechRecognizer.ERROR_AUDIO)

        // In continuous mode, silence or speech timeouts are normal thinking pauses!
        if (isContinuousListeningActive && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                             error == SpeechRecognizer.ERROR_NO_MATCH)) {
            Log.d(tag, "Pause detected (timeout $error). Seamlessly restarting continuous recognizer.")
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

        handleRecognitionError(errorMsg, isConflict)

        if (isContinuousListeningActive && !isConflict) {
            restartListeningSafely()
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognizedChunk = matches[0]
            Log.d(tag, "SpeechRecognizer result chunk: \"$recognizedChunk\"")
            scope.launch {
                handleIncomingSpeechChunk(recognizedChunk)
            }
        }

        // Keep listening across pauses if continuous session is active
        if (isContinuousListeningActive && _voiceState.value == JarvisVoiceState.CONVERSATION_LISTENING) {
            restartListeningSafely()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partial = matches[0]
            scope.launch {
                handlePartialSpeechChunk(partial)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun handleRecognitionError(message: String, isMicConflict: Boolean) {
        scope.launch {
            if (isMicConflict) {
                emergencyStop()
                _currentTask.value = message
                speak("Microphone is currently in use by another application.")
            } else if (_voiceState.value == JarvisVoiceState.CONVERSATION_LISTENING) {
                Log.d(tag, "Silence or thinking pause: keeping session active.")
            }
        }
    }

    // ==========================================
    // State Machine & Turn Handling
    // ==========================================

    private suspend fun handleWakeWordActivated() {
        wakeWordManager.stopListening()
        _voiceState.value = JarvisVoiceState.WAKE_DETECTED
        _currentTask.value = "Wake phrase detected: \"$wakePhrase\""
        updateForegroundNotification("Wake phrase detected: \"$wakePhrase\"")
        securityEventManager.logEvent(SecurityEventType.WAKE_DETECTED, "Wake phrase '$wakePhrase' recognized.")

        // Transition to AUTHENTICATING
        _voiceState.value = JarvisVoiceState.AUTHENTICATING
        _currentTask.value = "Authenticating speaker identity..."
        updateForegroundNotification("Authenticating speaker...")

        // Acoustic buffer verification
        val simulatedSample = ShortArray(1600) { 150 }
        val verification = speakerVerificationManager.verifySpeaker(simulatedSample)
        val antiSpoof = voiceAntiSpoofingManager.evaluateAudioStream(simulatedSample)

        if (antiSpoof.requiresChallenge) {
            val challenge = challengeResponseManager.generateFreshChallenge()
            pendingChallenge = challenge
            securityEventManager.logEvent(
                SecurityEventType.CHALLENGE_REQUESTED,
                "Dynamic anti-spoof challenge issued: ${challenge.challengePhrase}"
            )
            _voiceState.value = JarvisVoiceState.SPEAKING
            speak("Security verification required. Please repeat: ${challenge.challengePhrase}") {
                enterConversationListening()
            }
            return
        }

        when (verification) {
            is SpeakerVerificationResult.Authenticated -> {
                securityEventManager.logEvent(
                    SecurityEventType.SPEAKER_AUTHENTICATED,
                    "Speaker verified as ${verification.displayName} (confidence: ${(verification.confidence * 100).toInt()}%).",
                    verification.confidence
                )
                if (sayListeningAck) {
                    _voiceState.value = JarvisVoiceState.SPEAKING
                    speak("Yes, I'm listening.") {
                        enterConversationListening()
                    }
                } else {
                    enterConversationListening()
                }
            }
            is SpeakerVerificationResult.Uncertain -> {
                val challenge = challengeResponseManager.generateFreshChallenge()
                pendingChallenge = challenge
                securityEventManager.logEvent(
                    SecurityEventType.CHALLENGE_REQUESTED,
                    "Speaker uncertain (${verification.reason}). Prompting challenge."
                )
                _voiceState.value = JarvisVoiceState.SPEAKING
                speak("I noticed an unfamiliar tone. Please verify by saying: ${challenge.challengePhrase}") {
                    enterConversationListening()
                }
            }
            is SpeakerVerificationResult.UnknownSpeaker,
            is SpeakerVerificationResult.UnenrolledSystem -> {
                securityEventManager.logEvent(
                    SecurityEventType.SPEAKER_REJECTED_UNKNOWN,
                    "Unauthorized voice attempted wake word. Access denied."
                )
                _voiceState.value = JarvisVoiceState.SPEAKING
                speak("Voice identity not recognized. System remains locked.") {
                    finishSessionAndReturnToIdle()
                }
            }
        }
    }

    private fun enterConversationListening() {
        _voiceState.value = JarvisVoiceState.CONVERSATION_LISTENING
        _accumulatedTurnBuffer.value = ""
        val targetName = activeDeviceManager.activeDeviceName
        _currentTask.value = "Listening... (Target: $targetName)"
        updateForegroundNotification("Listening... (Say \"$endPhrase\")")

        startContinuousListening()

        // Turn timeout watchdog
        conversationTimeoutJob?.cancel()
        conversationTimeoutJob = scope.launch {
            delay(maxConversationSeconds * 1000L)
            if (_voiceState.value == JarvisVoiceState.CONVERSATION_LISTENING) {
                Log.d(tag, "Turn timeout reached ($maxConversationSeconds s). Automatically closing turn.")
                manualEndTurn()
            }
        }
    }

    private suspend fun handlePartialSpeechChunk(partialChunk: String) {
        if (_voiceState.value != JarvisVoiceState.CONVERSATION_LISTENING) return
        val trimmed = partialChunk.trim()
        if (trimmed.isEmpty()) return

        val previous = _accumulatedTurnBuffer.value
        val preview = if (previous.isEmpty()) trimmed else "$previous $trimmed"

        // Check if user has already spoken the end phrase in streaming audio
        if (isEndPhrasePresent(preview)) {
            Log.d(tag, "End phrase detected in partial audio stream: \"$preview\"")
            handleEndPhraseDetected(preview)
        }
    }

    private suspend fun handleIncomingSpeechChunk(chunk: String) {
        if (_voiceState.value != JarvisVoiceState.CONVERSATION_LISTENING) return

        val trimmed = chunk.trim()
        if (trimmed.isEmpty()) return

        // 1. If pending security challenge, evaluate response
        if (pendingChallenge != null) {
            val passed = challengeResponseManager.verifySpokenChallenge(trimmed)
            if (passed) {
                pendingChallenge = null
                speakerVerificationManager.forceAuthorizeForSession(UserRole.PRIMARY_OWNER)
                securityEventManager.logEvent(SecurityEventType.CHALLENGE_PASSED, "Dynamic voice challenge passed successfully.")
                speak("Authentication confirmed. Yes, I'm listening.") {
                    enterConversationListening()
                }
            } else {
                securityEventManager.logEvent(SecurityEventType.CHALLENGE_FAILED, "Failed dynamic challenge: '$trimmed'.")
                speak("Security challenge failed. Access denied.") {
                    finishSessionAndReturnToIdle()
                }
            }
            return
        }

        val previous = _accumulatedTurnBuffer.value
        val updated = if (previous.isEmpty()) trimmed else "$previous $trimmed"
        _accumulatedTurnBuffer.value = updated

        // 2. Check End-of-turn phrase
        if (isEndPhrasePresent(updated)) {
            handleEndPhraseDetected(updated)
        } else {
            _currentTask.value = "Listening... [${activeDeviceManager.activeDeviceName}]"
        }
    }

    /**
     * Detects if the user uttered the configured end phrase or standard variations.
     */
    private fun isEndPhrasePresent(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        val customEnd = endPhrase.lowercase(Locale.ROOT).trim()
        if (customEnd.isNotEmpty() && lower.contains(customEnd)) return true

        // Comprehensive end-phrase regex matching
        val standardPattern = Regex(
            """\b(jarvis\s*,?\s*)?(it\s+is\s+enough|it's\s+enough|that\s+is\s+all|that's\s+all|stop\s+listening|enough\s+jarvis|cancel\s+command)\b""",
            RegexOption.IGNORE_CASE
        )
        return standardPattern.containsMatchIn(text)
    }

    /**
     * Strips the end phrase from the accumulated speech string cleanly.
     */
    private fun stripEndPhrase(text: String): String {
        var clean = text
        val customEnd = endPhrase.trim()
        if (customEnd.isNotEmpty()) {
            clean = clean.replace(Regex("""(?i)\b${Regex.escape(customEnd)}\b"""), "")
        }
        clean = clean.replace(
            Regex("""(?i)\b(jarvis\s*,?\s*)?(it\s+is\s+enough|it's\s+enough|that\s+is\s+all|that's\s+all|stop\s+listening|enough\s+jarvis|cancel\s+command)\b"""),
            ""
        )
        return clean.trim().trimEnd('.', ',', '!', '?', ';')
    }

    private suspend fun handleEndPhraseDetected(fullSpokenText: String) {
        conversationTimeoutJob?.cancel()
        stopContinuousListening()

        _voiceState.value = JarvisVoiceState.END_PHRASE_DETECTED
        _currentTask.value = "End phrase recognized: Finalizing turn..."
        updateForegroundNotification("End phrase recognized. Processing...")

        val cleanedPrompt = stripEndPhrase(fullSpokenText)
        _accumulatedTurnBuffer.value = ""

        if (cleanedPrompt.isBlank()) {
            _voiceState.value = JarvisVoiceState.SPEAKING
            speak("Understood, standing by.") {
                finishSessionAndReturnToIdle()
            }
            return
        }

        _voiceState.value = JarvisVoiceState.PROCESSING
        _currentTask.value = "Thinking..."
        updateForegroundNotification("Thinking...")
        addMessage("USER", cleanedPrompt)

        // 1. Check for Active Target Device switch command
        val switchReply = activeDeviceManager.checkAndHandleDeviceSwitchCommand(cleanedPrompt)
        if (switchReply != null) {
            _voiceState.value = JarvisVoiceState.SPEAKING
            addMessage("JARVIS", switchReply)
            speak(switchReply) { finishSessionAndReturnToIdle() }
            return
        }

        // 2. Check for Bluetooth Audio device routing command
        val bluetoothReply = bluetoothAudioDeviceManager.handleSpokenAudioDeviceCommand(cleanedPrompt)
        if (bluetoothReply != null) {
            _voiceState.value = JarvisVoiceState.SPEAKING
            addMessage("JARVIS", bluetoothReply)
            speak(bluetoothReply) { finishSessionAndReturnToIdle() }
            return
        }

        // 3. Check if command is targeted to Windows Desktop
        if (activeDeviceManager.isTargetDesktop() || isDesktopTargetExplicit(cleanedPrompt)) {
            executeOnWindowsDesktop(cleanedPrompt)
            return
        }

        // 4. Execute on Local Android Tablet
        executeOnLocalTablet(cleanedPrompt)
    }

    private fun isDesktopTargetExplicit(query: String): Boolean {
        val q = query.lowercase(Locale.ROOT)
        return q.contains("on the desktop") || q.contains("on desktop") ||
               q.contains("on the computer") || q.contains("on my pc")
    }

    private suspend fun executeOnWindowsDesktop(query: String) {
        _currentTask.value = "Routing command to Windows 10 desktop..."
        updateForegroundNotification("Routing to Windows 10 Desktop...")
        val q = query.lowercase(Locale.ROOT)

        val actionType: String
        val payload = mutableMapOf<String, String>()

        when {
            q.contains("open chrome") || q.contains("launch chrome") -> {
                actionType = "LAUNCH_APP"
                payload["appName"] = "chrome"
            }
            q.contains("open notepad") -> {
                actionType = "LAUNCH_APP"
                payload["appName"] = "notepad"
            }
            q.contains("open calculator") || q.contains("calc") -> {
                actionType = "LAUNCH_APP"
                payload["appName"] = "calc"
            }
            q.contains("open vscode") || q.contains("open code") -> {
                actionType = "LAUNCH_APP"
                payload["appName"] = "code"
            }
            q.contains("open spotify") -> {
                actionType = "LAUNCH_APP"
                payload["appName"] = "spotify"
            }
            q.contains("volume up") || q.contains("turn it up") -> {
                actionType = "VOLUME"
                payload["direction"] = "up"
            }
            q.contains("volume down") -> {
                actionType = "VOLUME"
                payload["direction"] = "down"
            }
            q.contains("pause music") || q.contains("play music") -> {
                actionType = "MEDIA"
                payload["mediaAction"] = "play_pause"
            }
            q.contains("lock workstation") || q.contains("lock computer") || q.contains("lock pc") -> {
                actionType = "LOCK_WORKSTATION"
            }
            q.contains("desktop status") || q.contains("pc status") || q.contains("computer status") -> {
                actionType = "SYSTEM_INFO"
            }
            q.startsWith("type ") -> {
                actionType = "KEYSTROKE"
                payload["text"] = query.removePrefix("type ").trim()
            }
            else -> {
                actionType = "LAUNCH_APP"
                payload["appName"] = query.replace("on the desktop", "").replace("on desktop", "").trim()
            }
        }

        val result = crossDeviceSyncManager.sendActionToWindows(actionType, payload)
        _voiceState.value = JarvisVoiceState.SPEAKING
        _currentTask.value = "JARVIS is responding..."

        when (result) {
            is CrossDeviceResult.Success -> {
                addMessage("JARVIS", result.responseMessage, result.executionDetails)
                speak(result.responseMessage) { finishSessionAndReturnToIdle() }
            }
            is CrossDeviceResult.Failed -> {
                addMessage("JARVIS", result.honestReason, "Windows Dispatch Failure")
                speak(result.honestReason) { finishSessionAndReturnToIdle() }
            }
        }
    }

    private suspend fun executeOnLocalTablet(cleanedPrompt: String) {
        // Study Mode Step Check
        if (studyModeManager.isStudyModeActive.value) {
            val studyHandled = studyModeManager.processSpokenInput(cleanedPrompt) { reply ->
                _voiceState.value = JarvisVoiceState.SPEAKING
                _currentTask.value = "JARVIS is responding..."
                speak(reply) { finishSessionAndReturnToIdle() }
            }
            if (studyHandled) return
        }

        // Local Action Manager / Interpreter
        val actionResult = commandInterpreter.interpretAndExecute(
            query = cleanedPrompt,
            lastContext = lastDiscussedTarget,
            actionManager = actionManager,
            jarvisGuard = jarvisGuard
        )

        _voiceState.value = JarvisVoiceState.SPEAKING
        _currentTask.value = "JARVIS is responding..."

        when (actionResult) {
            is CommandInterpreter.Result.DirectSpokenResponse -> {
                addMessage("JARVIS", actionResult.message)
                speak(actionResult.message) { finishSessionAndReturnToIdle() }
            }
            is CommandInterpreter.Result.AppLaunchSuccess -> {
                lastDiscussedTarget = actionResult.appName
                addMessage("JARVIS", actionResult.spokenConfirmation, "Launched ${actionResult.appName} on Tablet")
                speak(actionResult.spokenConfirmation) { finishSessionAndReturnToIdle() }
            }
            is CommandInterpreter.Result.AccessibilityActionExecuted -> {
                addMessage("JARVIS", actionResult.spokenConfirmation, actionResult.detail)
                speak(actionResult.spokenConfirmation) { finishSessionAndReturnToIdle() }
            }
            is CommandInterpreter.Result.GuardConfirmationRequired -> {
                addMessage("JARVIS", actionResult.prompt, "JARVIS Guard Check")
                speak(actionResult.prompt) { finishSessionAndReturnToIdle() }
            }
            is CommandInterpreter.Result.RequiresClarification -> {
                addMessage("JARVIS", actionResult.question)
                speak(actionResult.question) { finishSessionAndReturnToIdle() }
            }
            is CommandInterpreter.Result.ActionFailed -> {
                addMessage("JARVIS", actionResult.honestReason)
                speak(actionResult.honestReason) { finishSessionAndReturnToIdle() }
            }
            is CommandInterpreter.Result.ForwardToGeminiReasoning -> {
                val geminiReply = aiProviderManager.queryActiveBrain(cleanedPrompt, _conversationHistory.value)
                addMessage("JARVIS", geminiReply)
                speak(geminiReply) { finishSessionAndReturnToIdle() }
            }
        }
    }

    private fun finishSessionAndReturnToIdle() {
        _voiceState.value = JarvisVoiceState.IDLE
        _currentTask.value = "Waiting for $wakePhrase"
        updateForegroundNotification("Waiting for \"$wakePhrase\"")
        if (_isHandsFreeEnabled.value) {
            startWakeWordDetection()
        }
    }

    private fun startWakeWordDetection() {
        _voiceState.value = JarvisVoiceState.IDLE
        _micActive.value = true
        _currentTask.value = "Waiting for '$wakePhrase'..."
        updateForegroundNotification("Listening for \"$wakePhrase\"...")
        wakeWordManager.startListening()
    }

    private fun addMessage(sender: String, text: String, actionDetail: String? = null) {
        val list = _conversationHistory.value.toMutableList()
        list.add(ConversationMessage(sender = sender, text = text, actionDetail = actionDetail))
        _conversationHistory.value = list
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousListening()
        wakeWordManager.stopListening()
        ttsManager.shutdown()
        speechRecognizer?.destroy()
        speechRecognizer = null
        scope.cancel()
        if (instance == this) {
            instance = null
        }
        Log.i(tag, "VoiceEngine Foreground Service destroyed.")
    }

    companion object {
        @Volatile
        var instance: VoiceEngine? = null
            private set

        const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.jarvis.assistant.VOICE_ENGINE_START"
        const val ACTION_STOP = "com.jarvis.assistant.VOICE_ENGINE_STOP"
        const val ACTION_START_HANDS_FREE = "com.jarvis.assistant.VOICE_ENGINE_START_HANDS_FREE"
        const val ACTION_STOP_HANDS_FREE = "com.jarvis.assistant.VOICE_ENGINE_STOP_HANDS_FREE"
        const val ACTION_START_MANUAL_INTERACTION = "com.jarvis.assistant.VOICE_ENGINE_MANUAL_INTERACTION"
        const val ACTION_END_TURN = "com.jarvis.assistant.VOICE_ENGINE_END_TURN"
        const val ACTION_EMERGENCY_STOP = "com.jarvis.assistant.VOICE_ENGINE_EMERGENCY_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, VoiceEngine::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceEngine::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
