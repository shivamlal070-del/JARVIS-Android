package com.jarvis.assistant.core.guard

import android.content.Context
import java.net.URI
import java.util.Locale

enum class GuardPolicy {
    ALWAYS_ASK,
    ASK_FOR_RISKY_ACTIONS,
    AUTOMATIC_FOR_LOW_RISK
}

class JarvisGuard(private val context: Context) {

    var currentPolicy: GuardPolicy = GuardPolicy.ASK_FOR_RISKY_ACTIONS

    fun isActionPermittedWithoutPrompt(isConsequential: Boolean): Boolean {
        return when (currentPolicy) {
            GuardPolicy.ALWAYS_ASK -> false
            GuardPolicy.ASK_FOR_RISKY_ACTIONS -> !isConsequential
            GuardPolicy.AUTOMATIC_FOR_LOW_RISK -> !isConsequential
        }
    }

    fun analyzeWebsiteSafety(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) {
            return "No active website URL was detected in your browser's address bar."
        }

        try {
            val uri = URI(if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) "https://$rawUrl" else rawUrl)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return "Unable to determine the website domain."
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "http"

            val concerns = mutableListOf<String>()

            // 1. Check protocol security
            if (scheme == "http") {
                concerns.add("This site uses unencrypted HTTP instead of secure HTTPS, so transmitted data can be intercepted.")
            }

            // 2. Check for IP address in URL
            if (host.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
                concerns.add("The link directly points to a raw IP address rather than an established domain name.")
            }

            // 3. Known high-risk or dynamic TLD patterns
            val suspiciousTlds = listOf(".top", ".xyz", ".click", ".buzz", ".work", ".stream")
            if (suspiciousTlds.any { host.endsWith(it) }) {
                concerns.add("The domain uses a top-level domain frequently associated with spam or disposable sites ($host).")
            }

            // 4. Multiple hyphens or deceptive brand typosquatting
            if (host.count { it == '-' } > 2) {
                concerns.add("The domain contains multiple hyphens, often used to mimic trusted brands.")
            }

            return if (concerns.isEmpty()) {
                "The domain $host uses secure HTTPS and has no immediate red flags based on standard URL safety heuristics."
            } else {
                "Sir, I have noticed the following concerns about $host: " + concerns.joinToString(" Also: ")
            }

        } catch (e: Exception) {
            return "Unable to parse this URL: ${e.message}"
        }
    }

    fun generateConsequentialMessageWarning(contactName: String, messageText: String): String {
        return "I have prepared the message to $contactName: \"$messageText\". Should I proceed with sending it?"
    }

    fun generateDestructiveActionWarning(actionName: String): String {
        return "Sir, you requested to $actionName. This is an irreversible action. Do you confirm?"
    }
}
