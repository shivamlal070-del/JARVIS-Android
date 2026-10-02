package com.jarvis.assistant.core.ai

import android.content.Context
import com.jarvis.assistant.core.device.AndroidActionManager
import com.jarvis.assistant.core.guard.JarvisGuard
import java.util.Locale

class CommandInterpreter(
    private val context: Context,
    private val aiProviderManager: AIProviderManager
) {
    sealed class Result {
        data class DirectSpokenResponse(val message: String) : Result()
        data class AppLaunchSuccess(val appName: String, val spokenConfirmation: String) : Result()
        data class AccessibilityActionExecuted(val spokenConfirmation: String, val detail: String) : Result()
        data class GuardConfirmationRequired(val prompt: String, val pendingAction: () -> Unit) : Result()
        data class RequiresClarification(val question: String) : Result()
        data class ActionFailed(val honestReason: String) : Result()
        object ForwardToGeminiReasoning : Result()
    }

    suspend fun interpretAndExecute(
        query: String,
        lastContext: String?,
        actionManager: AndroidActionManager,
        jarvisGuard: JarvisGuard
    ): Result {
        val q = query.trim().lowercase(Locale.ROOT)

        // 1. Contextual "Open it" resolution
        if (q == "open it" || q == "launch it") {
            if (lastContext != null) {
                return executeAppLaunch(lastContext, actionManager)
            } else {
                return Result.RequiresClarification("What app would you like me to open?")
            }
        }

        // 2. App Launch Commands
        if (q.startsWith("open ") || q.startsWith("launch ") || q.startsWith("switch to ")) {
            val target = q.removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("switch to ")
                .trim()
            return executeAppLaunch(target, actionManager)
        }

        // 3. Navigation: "Go back"
        if (q == "go back" || q == "previous screen" || q == "back") {
            val success = actionManager.accessibilityController.pressBack()
            return if (success) {
                Result.AccessibilityActionExecuted("Going back.", "Accessibility Global Action BACK")
            } else {
                Result.ActionFailed("I cannot navigate back because Accessibility Service is not enabled.")
            }
        }

        // 4. Screen Understanding: "Read what's on the screen", "What's on my screen"
        if (q.contains("what's on my screen") || q.contains("read screen") || q.contains("what is on the screen") || q.contains("read what's on the screen")) {
            val screenText = actionManager.accessibilityController.extractVisibleScreenText()
            return if (screenText.isNotBlank()) {
                val preview = screenText.take(300)
                Result.DirectSpokenResponse("On your screen, I can see: $preview")
            } else {
                Result.ActionFailed("I cannot read the current screen. Accessibility permission is either disabled or the app does not expose readable text.")
            }
        }

        // 5. Battery queries
        if (q.contains("battery")) {
            val batteryInfo = actionManager.batteryMonitor.getBatteryStatusSpoken()
            return Result.DirectSpokenResponse(batteryInfo)
        }

        // 6. Network / Data queries
        if (q.contains("data") || q.contains("wi-fi") || q.contains("wifi") || q.contains("internet")) {
            val netInfo = actionManager.dataUsageMonitor.getNetworkStatusSpoken()
            return Result.DirectSpokenResponse(netInfo)
        }

        // 7. Timer & Alarm commands
        if (q.contains("timer") || q.contains("remind") || q.contains("alarm")) {
            return actionManager.timerManager.handleNaturalTimerCommand(q)
        }

        // 8. AI Provider switching
        if (q.contains("switch to chatgpt") || q.contains("open chatgpt voice")) {
            aiProviderManager.setProvider("ChatGPT")
            return executeAppLaunch("ChatGPT", actionManager)
        }
        if (q.contains("switch to gemini") || q.contains("use gemini")) {
            aiProviderManager.setProvider("Gemini")
            return Result.DirectSpokenResponse("Switched reasoning engine to Gemini.")
        }

        // 9. Website safety audit
        if (q.contains("is this website safe") || q.contains("safe website") || q.contains("check website")) {
            val currentUrl = actionManager.accessibilityController.extractCurrentBrowserUrl()
            val audit = jarvisGuard.analyzeWebsiteSafety(currentUrl)
            return Result.DirectSpokenResponse(audit)
        }

        // 10. Study Session triggers
        if (q.contains("start study mode") || q.contains("start study session") || q.contains("start my study session")) {
            actionManager.timerManager.startStudyTimer(50)
            return Result.DirectSpokenResponse("Study mode activated. 50-minute focus session started. Show me your DPP question whenever you are ready.")
        }

        if (q.contains("stop study mode") || q.contains("stop study session")) {
            return Result.DirectSpokenResponse("Study session stopped.")
        }

        // 11. Fallback to Gemini Brain
        return Result.ForwardToGeminiReasoning
    }

    private fun executeAppLaunch(targetName: String, actionManager: AndroidActionManager): Result {
        val launchSuccess = actionManager.appLauncher.launchAppByName(targetName)
        return if (launchSuccess) {
            Result.AppLaunchSuccess(targetName, "Opening $targetName.")
        } else {
            val reason = actionManager.appLauncher.getLastLaunchFailureReason(targetName)
            Result.ActionFailed(reason)
        }
    }
}
