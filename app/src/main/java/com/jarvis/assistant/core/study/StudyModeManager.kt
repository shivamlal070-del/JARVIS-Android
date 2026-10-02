package com.jarvis.assistant.core.study

import android.content.Context
import com.jarvis.assistant.core.ai.AIProviderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class StudyModeManager(
    private val context: Context,
    private val aiProviderManager: AIProviderManager
) {
    private val _isStudyModeActive = MutableStateFlow(false)
    val isStudyModeActive: StateFlow<Boolean> = _isStudyModeActive.asStateFlow()

    val problemManager = StudyProblemManager()

    private val _currentProblemSubject = MutableStateFlow("Physics")
    val currentProblemSubject: StateFlow<String> = _currentProblemSubject.asStateFlow()

    fun startStudyMode(subject: String = "Physics") {
        _isStudyModeActive.value = true
        _currentProblemSubject.value = subject
    }

    fun stopStudyMode() {
        _isStudyModeActive.value = false
        problemManager.clearProblem()
    }

    fun submitProblem(subject: String, question: String, imageBase64: String? = null) {
        problemManager.setProblem(subject, question, imageBase64)
    }

    suspend fun processSpokenInput(query: String, speakCallback: (String) -> Unit): Boolean {
        val q = query.trim().lowercase()

        // Check if user requests the full solution
        if (q.contains("give me the solution") || q.contains("show full solution") || q.contains("what is the answer")) {
            val problem = problemManager.currentProblem
            if (problem == null) {
                speakCallback("No DPP question is currently active. Show or read me the question first.")
                return true
            }
            val prompt = "Provide the complete step-by-step solution for Class 11 ${problem.subject} problem: ${problem.questionText}."
            val solution = aiProviderManager.geminiEngine.generateResponse(prompt, emptyList())
            speakCallback(solution)
            return true
        }

        // Check if user is defining a new question
        if (q.startsWith("here is the question:") || q.startsWith("question:") || q.startsWith("the problem is")) {
            val questionText = query.substringAfter(":")
            submitProblem(_currentProblemSubject.value, questionText)
            speakCallback("Question saved for Class 11 ${_currentProblemSubject.value}. Please explain your first step aloud.")
            return true
        }

        // Check if user is explaining a step for the current active question
        val problem = problemManager.currentProblem
        if (problem != null) {
            val systemInstruction = """
                You are checking a Class 11 student's step-by-step solution to this problem:
                Subject: ${problem.subject}
                Problem Statement: ${problem.questionText}
                Previous verified steps: ${problem.steps.filter { it.isCorrect }.joinToString("; ") { it.userExplanation }}

                The student is stating their NEXT step: "$query"

                Rules:
                1. Check algebra, signs, units, physics principles (Newton's laws, conservation of energy/momentum, dimensional consistency), chemistry stoichiometry, or calculus/algebra assumptions.
                2. If the step is mathematically and conceptually sound, start your answer with "CORRECT: That step is correct. Continue."
                3. If the step is flawed, start with "INCORRECT: Stop there. That is the first incorrect step." Explain the mistake concisely (e.g. sign error, dividing by zero, missing cosine term) and give a helpful hint.
                4. NEVER reveal the final answer prematurely unless asked.
            """.trimIndent()

            val aiResponse = aiProviderManager.geminiEngine.generateResponse(query, emptyList(), systemInstruction)

            if (aiResponse.startsWith("CORRECT:")) {
                problemManager.addStepResult(query, true, aiResponse.removePrefix("CORRECT:").trim(), null)
                speakCallback(aiResponse.removePrefix("CORRECT:").trim())
            } else if (aiResponse.startsWith("INCORRECT:")) {
                val feedback = aiResponse.removePrefix("INCORRECT:").trim()
                problemManager.addStepResult(query, false, feedback, "Review physics conditions / signs.")
                speakCallback(feedback)
            } else {
                speakCallback(aiResponse)
            }
            return true
        }

        return false
    }
}
