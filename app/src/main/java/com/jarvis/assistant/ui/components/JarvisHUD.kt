package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.voice.JarvisVoiceState
import com.jarvis.assistant.ui.theme.*

@Composable
fun JarvisHUD(
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
    onMicClick: () -> Unit,
    onManualEndTurn: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hud_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val stateColor = when (voiceState) {
        JarvisVoiceState.IDLE -> JarvisNeonBlue
        JarvisVoiceState.WAKE_DETECTED -> JarvisCyan
        JarvisVoiceState.AUTHENTICATING -> JarvisWarningAmber
        JarvisVoiceState.CONVERSATION_LISTENING -> JarvisElectricTeal
        JarvisVoiceState.END_PHRASE_DETECTED -> JarvisSuccessGreen
        JarvisVoiceState.PROCESSING -> JarvisWarningAmber
        JarvisVoiceState.SPEAKING -> JarvisCyan
    }

    val stateLabel = when (voiceState) {
        JarvisVoiceState.IDLE -> "Waiting for Hello Jarvis"
        JarvisVoiceState.WAKE_DETECTED -> "Wake Phrase Detected"
        JarvisVoiceState.AUTHENTICATING -> "Authenticating Speaker..."
        JarvisVoiceState.CONVERSATION_LISTENING -> "Listening... (Say 'Jarvis, it is enough')"
        JarvisVoiceState.END_PHRASE_DETECTED -> "End Phrase Recognized"
        JarvisVoiceState.PROCESSING -> "Thinking..."
        JarvisVoiceState.SPEAKING -> "JARVIS is Responding..."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // System Status Telemetry Strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = JarvisSurfaceDark,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("USER: $currentUser", color = JarvisCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("TARGET: $activeTargetDevice", color = JarvisElectricTeal, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("AUDIO: $audioDevice", color = JarvisTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("SEC: $securityStatus", color = if (securityStatus.contains("Auth")) JarvisSuccessGreen else JarvisWarningAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("AI: $aiProvider", color = JarvisCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Futuristic Core Arc Visualizer
        Box(
            modifier = Modifier.size(175.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = (size.minDimension / 2) - 8.dp.toPx()

                // Outer segmented tech ring
                drawCircle(
                    color = stateColor.copy(alpha = 0.25f),
                    radius = radius,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Arc segments
                drawArc(
                    color = stateColor,
                    startAngle = rotation,
                    sweepAngle = 110f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawArc(
                    color = stateColor.copy(alpha = 0.6f),
                    startAngle = rotation + 180f,
                    sweepAngle = 80f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Central Pulsing Orb / Microphone Button
            Box(
                modifier = Modifier
                    .size((105 * (if (micActive) pulseScale else 1f)).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                stateColor.copy(alpha = 0.65f),
                                JarvisCardBg,
                                JarvisDeepBlack
                            )
                        )
                    )
                    .border(2.dp, stateColor, CircleShape)
                    .clickable { onMicClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (micActive) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Microphone",
                    tint = if (micActive) JarvisCyan else JarvisTextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State Badge
        Surface(
            color = stateColor.copy(alpha = 0.15f),
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stateLabel,
                    color = stateColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Task Readout
        Text(
            text = currentTask,
            color = JarvisTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        // Live Accumulated Speech Buffer display during CONVERSATION_LISTENING
        if (voiceState == JarvisVoiceState.CONVERSATION_LISTENING) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                color = JarvisDeepBlack,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACCUMULATING TURN SPEECH (PAUSES PRESERVED):",
                            color = JarvisCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target: $activeTargetDevice",
                            color = JarvisElectricTeal,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (accumulatedBuffer.isNotBlank()) "\"$accumulatedBuffer\"" else "Speak freely. You can pause to think for 10-20 seconds without interruption. Conclude with 'Jarvis, it is enough'.",
                        color = if (accumulatedBuffer.isNotBlank()) JarvisTextPrimary else JarvisTextSecondary.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontStyle = if (accumulatedBuffer.isNotBlank()) androidx.compose.ui.text.font.FontStyle.Normal else androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Manual End Turn & Emergency Stop buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onManualEndTurn,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(14.dp), tint = JarvisDeepBlack)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Finish Turn (It is enough)", color = JarvisDeepBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onEmergencyStop,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisErrorRed)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Emergency Stop", fontSize = 11.sp)
                }
            }
        }
    }
}
