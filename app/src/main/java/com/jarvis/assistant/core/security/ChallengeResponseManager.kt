package com.jarvis.assistant.core.security

import android.content.Context
import kotlin.random.Random

data class VoiceChallenge(
    val id: String = java.util.UUID.randomUUID().toString(),
    val challengePhrase: String,
    val expectedText: String,
    val createdAt: Long = System.currentTimeMillis()
)

class ChallengeResponseManager {

    private val wordPool = listOf(
        "open study mode", "verify quantum state", "alpha vector",
        "confirm security delta", "initialize physics", "calculate friction",
        "activate galaxy tab", "secure protocol"
    )

    private var activeChallenge: VoiceChallenge? = null

    /**
     * Generates a fresh, unpredictable dynamic phrase for liveness & anti-spoof verification.
     */
    fun generateFreshChallenge(): VoiceChallenge {
        val baseWord = wordPool.random()
        val randomPin = Random.nextInt(10, 99)
        val phrase = "$baseWord $randomPin"
        val challenge = VoiceChallenge(
            challengePhrase = phrase,
            expectedText = phrase
        )
        activeChallenge = challenge
        return challenge
    }

    fun verifySpokenChallenge(spokenText: String): Boolean {
        val challenge = activeChallenge ?: return false
        val cleanSpoken = spokenText.lowercase().replace("[^a-z0-9 ]".toRegex(), "").trim()
        val cleanExpected = challenge.expectedText.lowercase().trim()

        val isMatched = cleanSpoken.contains(cleanExpected) ||
                        cleanSpoken.contains(cleanExpected.replace(" ", ""))
        if (isMatched) {
            activeChallenge = null
        }
        return isMatched
    }

    fun clearActiveChallenge() {
        activeChallenge = null
    }
}
