package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.ai.AIProviderManager
import com.jarvis.assistant.core.memory.MemoryManager
import com.jarvis.assistant.core.voice.VoiceEngine
import com.jarvis.assistant.ui.theme.*

@Composable
fun SettingsScreen(
    aiProviderManager: AIProviderManager,
    memoryManager: MemoryManager,
    voiceEngine: VoiceEngine,
    onSaveApiKey: (String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    val activeProvider by aiProviderManager.activeProvider.collectAsState()
    val memories by memoryManager.memories.collectAsState()
    val isLongTermMemoryOn by memoryManager.isLongTermMemoryEnabled.collectAsState()
    var isSavedConfirmation by remember { mutableStateOf(false) }

    // Voice Turn Configuration state
    var wakePhraseInput by remember { mutableStateOf(voiceEngine.wakePhrase) }
    var endPhraseInput by remember { mutableStateOf(voiceEngine.endPhrase) }
    var sayAckToggle by remember { mutableStateOf(voiceEngine.sayListeningAck) }
    var maxDuration by remember { mutableStateOf(voiceEngine.maxConversationSeconds) }
    var isVoiceSettingsSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("SETTINGS & CONFIGURATION", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("AI Provider, Wake & End Phrases, API Keys & Privacy Controls", color = JarvisTextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Explicit Voice Turn Interaction Settings
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Hearing, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPLICIT VOICE TURN CONTROLS", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "JARVIS relies on explicit wake and end phrases instead of silence timeouts. You can pause to think for 10-20 seconds without being interrupted.",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wake Phrase
                Text("Wake Phrase (activates session from IDLE):", color = JarvisTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = wakePhraseInput,
                    onValueChange = { wakePhraseInput = it; isVoiceSettingsSaved = false },
                    placeholder = { Text("Default: Hello Jarvis", color = JarvisTextSecondary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorderCyan,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // End Phrase
                Text("End-of-Turn Phrase (authoritative signal to process and respond):", color = JarvisTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = endPhraseInput,
                    onValueChange = { endPhraseInput = it; isVoiceSettingsSaved = false },
                    placeholder = { Text("Default: Jarvis, it is enough", color = JarvisTextSecondary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorderCyan,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle: Say "Yes, I'm listening."
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spoken Acknowledgment", color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Say \"Yes, I'm listening.\" upon detecting wake phrase", color = JarvisTextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = sayAckToggle,
                        onCheckedChange = { sayAckToggle = it; isVoiceSettingsSaved = false }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Maximum Conversation Duration
                Text("Maximum Conversation Duration (safety guard):", color = JarvisTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60 to "1m", 120 to "2m", 300 to "5m", 600 to "10m").forEach { (secs, label) ->
                        FilterChip(
                            selected = maxDuration == secs,
                            onClick = { maxDuration = secs; isVoiceSettingsSaved = false },
                            label = { Text(label, color = if (maxDuration == secs) JarvisDeepBlack else JarvisCyan) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan,
                                containerColor = JarvisSurfaceDark
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            voiceEngine.wakePhrase = wakePhraseInput.trim().ifEmpty { "Hello Jarvis" }
                            voiceEngine.endPhrase = endPhraseInput.trim().ifEmpty { "Jarvis, it is enough" }
                            voiceEngine.sayListeningAck = sayAckToggle
                            voiceEngine.maxConversationSeconds = maxDuration
                            isVoiceSettingsSaved = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                    ) {
                        Text("Save Voice Settings", color = JarvisDeepBlack, fontWeight = FontWeight.Bold)
                    }

                    if (isVoiceSettingsSaved) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("✓ Saved", color = JarvisSuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Emergency Stop Button
                OutlinedButton(
                    onClick = { voiceEngine.emergencyStop() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisErrorRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emergency Stop / Reset Microphone", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. AI Provider Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("PRIMARY AI REASONING ENGINE", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Select the default model for reasoning, conversational responses, and DPP problem checks.", color = JarvisTextSecondary, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Gemini", "ChatGPT").forEach { provider ->
                        FilterChip(
                            selected = activeProvider == provider,
                            onClick = { aiProviderManager.setProvider(provider) },
                            label = { Text(provider, color = if (activeProvider == provider) JarvisDeepBlack else JarvisCyan) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan,
                                containerColor = JarvisSurfaceDark
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Gemini API Key Secure Configuration
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = JarvisElectricTeal, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GOOGLE GEMINI API KEY", color = JarvisElectricTeal, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Stored securely in Android EncryptedSharedPreferences on your tablet. Never transmitted to third parties or logged.",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it; isSavedConfirmation = false },
                    placeholder = { Text("Paste your Gemini API key (AIzaSy…)", color = JarvisTextSecondary, fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorderCyan,
                        focusedTextColor = JarvisTextPrimary,
                        unfocusedTextColor = JarvisTextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                onSaveApiKey(apiKeyInput.trim())
                                isSavedConfirmation = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonBlue)
                    ) {
                        Text("Save Key Securely")
                    }

                    if (isSavedConfirmation) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("✓ Saved", color = JarvisSuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Memory Controls
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("USER MEMORY & CONTEXT", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Switch(
                        checked = isLongTermMemoryOn,
                        onCheckedChange = { memoryManager.setLongTermMemoryEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Stored facts, study topics, or preferences remembered across sessions.", color = JarvisTextSecondary, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(10.dp))

                if (memories.isEmpty()) {
                    Text("No stored memories.", color = JarvisTextSecondary, fontSize = 12.sp)
                } else {
                    memories.forEach { mem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${mem.key}: ${mem.value}", color = JarvisTextPrimary, fontSize = 13.sp)
                            IconButton(onClick = { memoryManager.deleteMemory(mem.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = JarvisErrorRed, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { memoryManager.clearAllMemories() }) {
                        Text("Clear All Memories", color = JarvisErrorRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
