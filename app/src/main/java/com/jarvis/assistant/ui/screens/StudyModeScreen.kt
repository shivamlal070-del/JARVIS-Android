package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.study.DPPProblem
import com.jarvis.assistant.core.study.StudyModeManager
import com.jarvis.assistant.ui.theme.*

@Composable
fun StudyModeScreen(
    studyModeManager: StudyModeManager,
    onLaunchCameraForDPP: () -> Unit,
    onSubmitNewQuestion: (subject: String, question: String) -> Unit,
    onVerifyStep: (String) -> Unit,
    onRequestFullSolution: () -> Unit
) {
    var selectedSubject by remember { mutableStateOf("Physics") }
    var newQuestionInput by remember { mutableStateOf("") }
    var stepInput by remember { mutableStateOf("") }

    val currentProblem = studyModeManager.problemManager.currentProblem
    val isStudyActive by studyModeManager.isStudyModeActive.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("STUDY MODE — CLASS 11 DPP", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Physics • Chemistry • Mathematics Step-by-Step Verifier", color = JarvisTextSecondary, fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    if (isStudyActive) studyModeManager.stopStudyMode() else studyModeManager.startStudyMode(selectedSubject)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStudyActive) JarvisErrorRed.copy(alpha = 0.8f) else JarvisNeonBlue
                )
            ) {
                Text(if (isStudyActive) "End Session" else "Start 50m Focus")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subject Selector Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Physics", "Chemistry", "Mathematics").forEach { subject ->
                FilterChip(
                    selected = selectedSubject == subject,
                    onClick = { selectedSubject = subject },
                    label = { Text(subject, color = if (selectedSubject == subject) JarvisDeepBlack else JarvisCyan) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = JarvisCyan,
                        containerColor = JarvisCardBg
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (currentProblem == null) {
            // No problem active card: Provide Input or Camera option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add a Class 11 DPP Problem", color = JarvisCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Photograph a textbook page / DPP sheet or enter the problem text. JARVIS will evaluate your solution step by step.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newQuestionInput,
                        onValueChange = { newQuestionInput = it },
                        placeholder = { Text("e.g., A block of mass 2 kg slides down a 30° rough incline with coefficient of friction μ = 0.2...", color = JarvisTextSecondary, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorderCyan,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (newQuestionInput.isNotBlank()) {
                                    onSubmitNewQuestion(selectedSubject, newQuestionInput)
                                    newQuestionInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonBlue)
                        ) {
                            Text("Set as Current Problem")
                        }

                        OutlinedButton(
                            onClick = onLaunchCameraForDPP,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capture DPP Page")
                        }
                    }
                }
            }
        } else {
            // Active Problem Card & Step-by-Step Verifier
            ActiveProblemCard(
                problem = currentProblem,
                stepInput = stepInput,
                onStepInputChange = { stepInput = it },
                onVerifyStepClick = {
                    if (stepInput.isNotBlank()) {
                        onVerifyStep(stepInput)
                        stepInput = ""
                    }
                },
                onRequestFullSolution = onRequestFullSolution,
                onClearProblem = { studyModeManager.problemManager.clearProblem() }
            )
        }
    }
}

@Composable
fun ActiveProblemCard(
    problem: DPPProblem,
    stepInput: String,
    onStepInputChange: (String) -> Unit,
    onVerifyStepClick: () -> Unit,
    onRequestFullSolution: () -> Unit,
    onClearProblem: () -> Unit
) {
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
                Surface(
                    color = JarvisNeonBlue.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "ACTIVE PROBLEM: ${problem.subject.uppercase()}",
                        color = JarvisCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                TextButton(onClick = onClearProblem) {
                    Text("Clear Problem", color = JarvisErrorRed, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = problem.questionText,
                color = JarvisTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "STEP-BY-STEP VERIFICATION HISTORY",
                color = JarvisTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisDeepBlack)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (problem.steps.isEmpty()) {
                    item {
                        Text(
                            "No steps evaluated yet. Speak or type your first step (e.g., \"First, resolve forces along the inclined plane: mg sin θ - fk = ma\").",
                            color = JarvisTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    items(problem.steps) { step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = if (step.isCorrect) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (step.isCorrect) JarvisSuccessGreen else JarvisErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Step ${step.stepIndex}: ${step.userExplanation}",
                                    color = JarvisTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = step.feedback,
                                    color = if (step.isCorrect) JarvisSuccessGreen else JarvisErrorRed,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Step Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stepInput,
                    onValueChange = onStepInputChange,
                    placeholder = { Text("State your next step aloud or type it…", color = JarvisTextSecondary, fontSize = 12.sp) },
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
                Button(
                    onClick = onVerifyStepClick,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Check Step", color = JarvisDeepBlack, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRequestFullSolution,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisWarningAmber)
            ) {
                Text("Need help? Show complete derivation & solution", fontSize = 12.sp)
            }
        }
    }
}
