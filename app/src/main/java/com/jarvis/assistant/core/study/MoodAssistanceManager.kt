package com.jarvis.assistant.core.study

enum class DetectedMood {
    NEUTRAL,
    FRUSTRATED,
    TIRED,
    STRESSED,
    DEMOTIVATED,
    EXCITED
}

data class MoodRecommendation(
    val mood: DetectedMood,
    val spokenSuggestion: String,
    val offerBreak: Boolean = false,
    val offerMusic: Boolean = false,
    val offerSimplerHint: Boolean = false
)

/**
 * MoodAssistanceManager analyzes conversational cues cautiously without psychological diagnosis,
 * offering supportive coaching, breaks, simpler pedagogical hints, or calming music.
 */
class MoodAssistanceManager {
    var isMoodAssistanceEnabled: Boolean = true
    var isAutomaticMusicEnabled: Boolean = true
    var isBreakSuggestionsEnabled: Boolean = true
    var isProactiveCoachingEnabled: Boolean = true

    fun analyzeInteraction(userUtterance: String): MoodRecommendation? {
        if (!isMoodAssistanceEnabled) return null

        val lower = userUtterance.lowercase()

        // Frustration cues
        if (lower.contains("frustrat") || lower.contains("hate this") || lower.contains("impossible") ||
            lower.contains("makes no sense") || lower.contains("give up") || lower.contains("stupid question") ||
            lower.contains("annoying")
        ) {
            return MoodRecommendation(
                mood = DetectedMood.FRUSTRATED,
                spokenSuggestion = "You sound frustrated. Would you like a simpler hint, or perhaps a short 5-minute break?",
                offerBreak = isBreakSuggestionsEnabled,
                offerMusic = isAutomaticMusicEnabled,
                offerSimplerHint = true
            )
        }

        // Fatigue cues
        if (lower.contains("tired") || lower.contains("exhausted") || lower.contains("sleepy") ||
            lower.contains("can't concentrate") || lower.contains("cant focus") || lower.contains("brain is fried") ||
            lower.contains("need coffee")
        ) {
            return MoodRecommendation(
                mood = DetectedMood.TIRED,
                spokenSuggestion = "It seems you may be getting tired. A 5-minute recovery break or some calming focus music might help recharge.",
                offerBreak = isBreakSuggestionsEnabled,
                offerMusic = isAutomaticMusicEnabled
            )
        }

        // Stress cues
        if (lower.contains("stressed") || lower.contains("overwhelmed") || lower.contains("panic") ||
            lower.contains("exam is tomorrow") || lower.contains("too much work")
        ) {
            return MoodRecommendation(
                mood = DetectedMood.STRESSED,
                spokenSuggestion = "I understand this is a challenging topic. Let's take it one step at a time.",
                offerBreak = isBreakSuggestionsEnabled,
                offerMusic = isAutomaticMusicEnabled
            )
        }

        // Demotivation cues
        if (lower.contains("what's the point") || lower.contains("i can't do this") || lower.contains("i'm bad at") ||
            lower.contains("not smart enough")
        ) {
            return MoodRecommendation(
                mood = DetectedMood.DEMOTIVATED,
                spokenSuggestion = "Class 11 concepts take patience to master. You've made solid progress—let's break down the next step together.",
                offerSimplerHint = true
            )
        }

        // Excitement cues
        if (lower.contains("got it") || lower.contains("solved it") || lower.contains("eureka") ||
            lower.contains("i understand now") || lower.contains("awesome")
        ) {
            return MoodRecommendation(
                mood = DetectedMood.EXCITED,
                spokenSuggestion = "Excellent work! That derivation is mathematically sound."
            )
        }

        return null
    }
}
