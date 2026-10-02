package com.jarvis.assistant.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.core.permissions.PermissionManager
import com.jarvis.assistant.service.JarvisForegroundVoiceService
import com.jarvis.assistant.ui.screens.*
import com.jarvis.assistant.ui.theme.JarvisDeepBlack
import com.jarvis.assistant.ui.theme.JarvisNeonBlue
import com.jarvis.assistant.ui.theme.JarvisSurfaceDark
import com.jarvis.assistant.ui.theme.JarvisTheme
import kotlinx.coroutines.launch

enum class JarvisScreen(val title: String, val icon: ImageVector) {
    HOME("JARVIS HUD", Icons.Default.GraphicEq),
    STUDY_MODE("Study DPP", Icons.Default.School),
    DEVICE_CONTROL("Controller", Icons.Default.Smartphone),
    GUARD("Guard", Icons.Default.Security),
    PERMISSIONS("Setup", Icons.Default.FactCheck),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            Toast.makeText(this, "Microphone permission granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "DPP problem image captured for analysis.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = JarvisApp.instance
        val permissionManager = PermissionManager(this)

        setContent {
            JarvisTheme {
                var currentScreen by remember { mutableStateOf(JarvisScreen.HOME) }

                val voiceState by app.voiceEngine.voiceState.collectAsState()
                val currentTask by app.voiceEngine.currentTask.collectAsState()
                val accumulatedBuffer by app.voiceEngine.accumulatedTurnBuffer.collectAsState()
                val micActive by app.voiceEngine.micActive.collectAsState()
                val isHandsFree by app.voiceEngine.isHandsFreeEnabled.collectAsState()
                val conversationHistory by app.voiceEngine.conversationHistory.collectAsState()

                val currentUser by app.speakerVerificationManager.currentUserName.collectAsState()
                val activeTargetDevice = app.activeDeviceManager.activeDeviceName
                val audioDevice by app.bluetoothAudioDeviceManager.activeAudioDevice.collectAsState()
                val isAuthenticated by app.speakerVerificationManager.isAuthenticated.collectAsState()
                val securityStatus = if (isAuthenticated) "Authenticated" else "Locked"
                val aiProvider by app.aiProviderManager.activeProvider.collectAsState()

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(JarvisDeepBlack)
                ) {
                    // Left Navigation Rail for Tablet landscape (Samsung Tab S5e)
                    NavigationRail(
                        containerColor = JarvisSurfaceDark,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(80.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        JarvisScreen.values().forEach { screen ->
                            NavigationRailItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Main Content Canvas
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentScreen) {
                            JarvisScreen.HOME -> HomeScreen(
                                voiceState = voiceState,
                                currentTask = currentTask,
                                accumulatedBuffer = accumulatedBuffer,
                                currentUser = currentUser,
                                activeTargetDevice = activeTargetDevice,
                                audioDevice = audioDevice,
                                securityStatus = securityStatus,
                                aiProvider = aiProvider,
                                micActive = micActive,
                                isHandsFree = isHandsFree,
                                conversationHistory = conversationHistory,
                                onMicClick = {
                                    app.voiceEngine.startManualVoiceInteraction()
                                },
                                onManualEndTurn = {
                                    app.voiceEngine.manualEndTurn()
                                },
                                onEmergencyStop = {
                                    app.voiceEngine.emergencyStop()
                                },
                                onToggleHandsFree = {
                                    if (isHandsFree) {
                                        val stopIntent = Intent(this@MainActivity, JarvisForegroundVoiceService::class.java).apply {
                                            action = JarvisForegroundVoiceService.ACTION_STOP
                                        }
                                        startService(stopIntent)
                                    } else {
                                        val startIntent = Intent(this@MainActivity, JarvisForegroundVoiceService::class.java).apply {
                                            action = JarvisForegroundVoiceService.ACTION_START
                                        }
                                        startService(startIntent)
                                    }
                                },
                                onManualCommandSubmit = { command ->
                                    lifecycleScope.launch {
                                        app.voiceEngine.commandInterpreter.interpretAndExecute(
                                            query = command,
                                            lastContext = null,
                                            actionManager = app.actionManager,
                                            jarvisGuard = app.jarvisGuard
                                        )
                                    }
                                },
                                onQuickAction = { query ->
                                    lifecycleScope.launch {
                                        app.voiceEngine.commandInterpreter.interpretAndExecute(
                                            query = query,
                                            lastContext = null,
                                            actionManager = app.actionManager,
                                            jarvisGuard = app.jarvisGuard
                                        )
                                    }
                                }
                            )

                            JarvisScreen.STUDY_MODE -> StudyModeScreen(
                                studyModeManager = app.studyModeManager,
                                onLaunchCameraForDPP = {
                                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                                    cameraLauncher.launch(takePictureIntent)
                                },
                                onSubmitNewQuestion = { subject, question ->
                                    app.studyModeManager.submitProblem(subject, question)
                                },
                                onVerifyStep = { step ->
                                    lifecycleScope.launch {
                                        app.studyModeManager.processSpokenInput(step) { reply ->
                                            app.voiceEngine.speak(reply)
                                        }
                                    }
                                },
                                onRequestFullSolution = {
                                    lifecycleScope.launch {
                                        app.studyModeManager.processSpokenInput("give me the solution") { reply ->
                                            app.voiceEngine.speak(reply)
                                        }
                                    }
                                }
                            )

                            JarvisScreen.DEVICE_CONTROL -> DeviceControlScreen(
                                actionManager = app.actionManager,
                                onLaunchApp = { appName ->
                                    app.actionManager.appLauncher.launchAppByName(appName)
                                },
                                onOpenAccessibilitySettings = {
                                    app.actionManager.accessibilityController.openAccessibilitySettings()
                                }
                            )

                            JarvisScreen.GUARD -> GuardScreen(
                                jarvisGuard = app.jarvisGuard,
                                onAuditUrl = { url ->
                                    // Audited in GuardScreen
                                }
                            )

                            JarvisScreen.PERMISSIONS -> PermissionsScreen(
                                permissionManager = permissionManager,
                                onRequestPermission = { perm ->
                                    if (perm.isSpecialAccess) {
                                        app.actionManager.accessibilityController.openAccessibilitySettings()
                                    } else {
                                        permissionLauncher.launch(arrayOf(perm.permissionKey))
                                    }
                                }
                            )

                            JarvisScreen.SETTINGS -> SettingsScreen(
                                aiProviderManager = app.aiProviderManager,
                                memoryManager = app.memoryManager,
                                voiceEngine = app.voiceEngine,
                                onSaveApiKey = { key ->
                                    app.aiProviderManager.geminiEngine.saveApiKey(key)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
