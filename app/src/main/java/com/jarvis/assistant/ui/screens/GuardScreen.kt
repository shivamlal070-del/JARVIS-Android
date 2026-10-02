package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.guard.GuardPolicy
import com.jarvis.assistant.core.guard.JarvisGuard
import com.jarvis.assistant.ui.theme.*

@Composable
fun GuardScreen(
    jarvisGuard: JarvisGuard,
    onAuditUrl: (String) -> Unit
) {
    var testUrlInput by remember { mutableStateOf("") }
    var selectedPolicy by remember { mutableStateOf(jarvisGuard.currentPolicy) }
    var auditResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("JARVIS GUARD", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Proactive Security, Permission & Data Awareness", color = JarvisTextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Protection Policy Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ACTION CONFIRMATION POLICY", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Select how cautiously JARVIS handles actions like sending messages, clicking buttons, downloading large files, or modifying tablet settings.",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                listOf(
                    GuardPolicy.ALWAYS_ASK to "Always Ask (Maximum Security)",
                    GuardPolicy.ASK_FOR_RISKY_ACTIONS to "Ask for Risky Actions (Recommended)",
                    GuardPolicy.AUTOMATIC_FOR_LOW_RISK to "Automatic for Low-Risk Actions"
                ).forEach { (policy, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedPolicy == policy,
                            onClick = {
                                selectedPolicy = policy
                                jarvisGuard.currentPolicy = policy
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = JarvisCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, color = JarvisTextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Website Safety Audit Tool
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("WEBSITE SAFETY AUDITOR", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Heuristically analyzes SSL encryption, suspicious TLDs, IP direct addresses, and phishing patterns.",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testUrlInput,
                    onValueChange = { testUrlInput = it },
                    placeholder = { Text("https://example.com or any web link", color = JarvisTextSecondary, fontSize = 12.sp) },
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

                Button(
                    onClick = {
                        if (testUrlInput.isNotBlank()) {
                            auditResult = jarvisGuard.analyzeWebsiteSafety(testUrlInput)
                            onAuditUrl(testUrlInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonBlue)
                ) {
                    Text("Analyze Safety Heuristics")
                }

                if (auditResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = JarvisDeepBlack,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisBorderCyan)
                    ) {
                        Text(
                            text = auditResult!!,
                            color = JarvisTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security Core Principles Notice
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = JarvisElectricTeal, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GUARANTEED INTEGRITY RULES", color = JarvisElectricTeal, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Never makes purchases without explicit authorization.", color = JarvisTextSecondary, fontSize = 12.sp)
                Text("• Never sends messages without preview confirmation.", color = JarvisTextSecondary, fontSize = 12.sp)
                Text("• Never deletes files or uninstalls applications secretly.", color = JarvisTextSecondary, fontSize = 12.sp)
                Text("• Never fabricates security warnings or data numbers.", color = JarvisTextSecondary, fontSize = 12.sp)
            }
        }
    }
}
