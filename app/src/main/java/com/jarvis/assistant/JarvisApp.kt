package com.jarvis.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.jarvis.assistant.core.ai.AIProviderManager
import com.jarvis.assistant.core.device.AndroidActionManager
import com.jarvis.assistant.core.device.BluetoothAudioDeviceManager
import com.jarvis.assistant.core.guard.JarvisGuard
import com.jarvis.assistant.core.memory.MemoryManager
import com.jarvis.assistant.core.security.*
import com.jarvis.assistant.core.study.StudyModeManager
import com.jarvis.assistant.core.sync.*
import com.jarvis.assistant.core.voice.VoiceEngine

class JarvisApp : Application() {

    companion object {
        lateinit var instance: JarvisApp
            private set

        const val CHANNEL_ID_VOICE = "jarvis_voice_channel"
        const val CHANNEL_ID_TIMERS = "jarvis_timer_channel"
        const val CHANNEL_ID_ALERTS = "jarvis_alert_channel"
    }

    // Core Managers
    lateinit var voiceEngine: VoiceEngine
        private set
    lateinit var aiProviderManager: AIProviderManager
        private set
    lateinit var actionManager: AndroidActionManager
        private set
    lateinit var jarvisGuard: JarvisGuard
        private set
    lateinit var studyModeManager: StudyModeManager
        private set
    lateinit var memoryManager: MemoryManager
        private set

    // Cross-Device & Sync Subsystems
    lateinit var deviceIdentityManager: DeviceIdentityManager
        private set
    lateinit var deviceRegistry: DeviceRegistry
        private set
    lateinit var activeDeviceManager: ActiveDeviceManager
        private set
    lateinit var crossDeviceSyncManager: CrossDeviceSyncManager
        private set
    lateinit var sessionManager: SessionManager
        private set

    // Security & Voice Authentication Subsystems
    lateinit var speakerVerificationManager: SpeakerVerificationManager
        private set
    lateinit var voiceAntiSpoofingManager: VoiceAntiSpoofingManager
        private set
    lateinit var challengeResponseManager: ChallengeResponseManager
        private set
    lateinit var adaptiveVoiceManager: AdaptiveVoiceManager
        private set
    lateinit var securityEventManager: SecurityEventManager
        private set

    // Hardware Audio
    lateinit var bluetoothAudioDeviceManager: BluetoothAudioDeviceManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        // 1. Initialize Sync & Device Mesh Subsystems
        deviceIdentityManager = DeviceIdentityManager(this)
        deviceRegistry = DeviceRegistry(this)
        activeDeviceManager = ActiveDeviceManager(this, deviceRegistry)
        crossDeviceSyncManager = CrossDeviceSyncManager(this, deviceIdentityManager, deviceRegistry)
        sessionManager = SessionManager(this)

        // 2. Initialize Security Subsystems
        securityEventManager = SecurityEventManager(this)
        speakerVerificationManager = SpeakerVerificationManager(this)
        voiceAntiSpoofingManager = VoiceAntiSpoofingManager(this)
        challengeResponseManager = ChallengeResponseManager()
        adaptiveVoiceManager = AdaptiveVoiceManager(this, speakerVerificationManager)

        // 3. Initialize Audio Hardware
        bluetoothAudioDeviceManager = BluetoothAudioDeviceManager(this)

        // 4. Initialize Core AI & Actions
        memoryManager = MemoryManager(this)
        aiProviderManager = AIProviderManager(this)
        jarvisGuard = JarvisGuard(this)
        actionManager = AndroidActionManager(this)
        studyModeManager = StudyModeManager(this, aiProviderManager)

        // 5. Initialize VoiceEngine with complete Security & Cross-Device wiring
        voiceEngine = VoiceEngine(
            this,
            aiProviderManager,
            actionManager,
            studyModeManager,
            jarvisGuard,
            activeDeviceManager,
            crossDeviceSyncManager,
            speakerVerificationManager,
            voiceAntiSpoofingManager,
            challengeResponseManager,
            securityEventManager,
            bluetoothAudioDeviceManager
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val voiceChannel = NotificationChannel(
                CHANNEL_ID_VOICE,
                "JARVIS Active Listening Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing hands-free wake-word detection status."
            }

            val timerChannel = NotificationChannel(
                CHANNEL_ID_TIMERS,
                "JARVIS Timers & Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications and spoken alerts for timers and reminders."
                enableVibration(true)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "JARVIS Guard Security Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Security, permission and large data alerts."
            }

            notificationManager.createNotificationChannels(listOf(voiceChannel, timerChannel, alertChannel))
        }
    }
}
