package com.jarvis.assistant.core.study

data class StudyStep(
    val stepIndex: Int,
    val userExplanation: String,
    val isCorrect: Boolean,
    val feedback: String,
    val hint: String? = null
)

data class DPPProblem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val subject: String, // "Physics", "Chemistry", "Mathematics"
    val questionText: String,
    val imageBase64: String? = null,
    val steps: MutableList<StudyStep> = mutableListOf(),
    var isSolved: Boolean = false
)

class StudyProblemManager {
    var currentProblem: DPPProblem? = null
        private set

    fun setProblem(subject: String, question: String, imageBase64: String? = null) {
        currentProblem = DPPProblem(
            subject = subject,
            questionText = question,
            imageBase64 = imageBase64
        )
    }

    fun addStepResult(explanation: String, isCorrect: Boolean, feedback: String, hint: String?) {
        val problem = currentProblem ?: return
        val step = StudyStep(
            stepIndex = problem.steps.size + 1,
            userExplanation = explanation,
            isCorrect = isCorrect,
            feedback = feedback,
            hint = hint
        )
        problem.steps.add(step)
    }

    fun clearProblem() {
        currentProblem = null
    }
}
