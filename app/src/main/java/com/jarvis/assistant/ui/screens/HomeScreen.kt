package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.voice.ConversationMessage
import com.jarvis.assistant.core.voice.JarvisVoiceState
import com.jarvis.assistant.ui.components.JarvisHUD
import com.jarvis.assistant.ui.theme.*

@Composable
fun HomeScreen(
    voiceState: JarvisVoiceState,
    currentTask: String,
    accumulatedBuffer: String,
    currentUser: String,
    activeTargetDevice: String,
    audioDevice: String,
    securityStatus: String,
    aiProvider: String,
    micActive: Boolean,
    isHandsFree: Boolean,
    conversationHistory: List<ConversationMessage>,
    onMicClick: () -> Unit,
    onManualEndTurn: () -> Unit,
    onEmergencyStop: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onManualCommandSubmit: (String) -> Unit,
    onQuickAction: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
            .padding(16.dp)
    ) {
        // Top HUD Area with Animated Arc and State Indicator
        JarvisHUD(
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
            onMicClick = onMicClick,
            onManualEndTurn = onManualEndTurn,
            onEmergencyStop = onEmergencyStop
        )

        // Hands-Free Mode Toggle Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisCardBg)
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = if (isHandsFree) JarvisCyan else JarvisTextSecondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hands-Free Wake Word",
                        color = JarvisTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isHandsFree) "Active — say \"Hello Jarvis\"" else "Disabled — tap to activate background listening",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Switch(
                checked = isHandsFree,
                onCheckedChange = { onToggleHandsFree() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = JarvisCyan,
                    checkedTrackColor = JarvisNeonBlue.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Command Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { onQuickAction("Open YouTube") },
                label = { Text("Open YouTube", color = JarvisCyan, fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { onQuickAction("Can you start a 25-minute study timer?") },
                label = { Text("25m Study Timer", color = JarvisCyan, fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { onQuickAction("How much battery do I have?") },
                label = { Text("Check Battery", color = JarvisCyan, fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { onQuickAction("Read what's on the screen") },
                label = { Text("Read Screen", color = JarvisCyan, fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Conversation Stream
        Text(
            text = "CONVERSATION STREAM",
            color = JarvisTextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurfaceDark)
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (conversationHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Say \"Hello Jarvis\" or tap the microphone to begin.",
                            color = JarvisTextSecondary.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(conversationHistory) { msg ->
                    ConversationItem(msg)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Manual text command input fallback
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type a voice command fallback…", color = JarvisTextSecondary, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisBorderCyan,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onManualCommandSubmit(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisNeonBlue)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = JarvisTextPrimary)
            }
        }
    }
}

@Composable
fun ConversationItem(msg: ConversationMessage) {
    val isUser = msg.sender == "USER"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) JarvisCardBg else JarvisDeepBlack,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) JarvisNeonBlue.copy(alpha = 0.5f) else JarvisBorderCyan
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "YOU" else "JARVIS",
                    color = if (isUser) JarvisCyan else JarvisElectricTeal,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    color = JarvisTextPrimary,
                    fontSize = 14.sp
                )
                if (msg.actionDetail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "▶ ${msg.actionDetail}",
                        color = JarvisElectricTeal,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
