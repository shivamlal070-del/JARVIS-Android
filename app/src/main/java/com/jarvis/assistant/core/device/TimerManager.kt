package com.jarvis.assistant.core.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.jarvis.assistant.core.ai.CommandInterpreter
import java.util.regex.Pattern

data class ActiveTimer(
    val id: Int,
    val title: String,
    val durationMinutes: Int,
    val triggerTimeMillis: Long
)

class TimerManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val activeTimers = mutableListOf<ActiveTimer>()

    fun handleNaturalTimerCommand(query: String): CommandInterpreter.Result {
        val q = query.lowercase()

        // 1. Check remaining time
        if (q.contains("how much time is left") || q.contains("time left")) {
            if (activeTimers.isEmpty()) {
                return CommandInterpreter.Result.DirectSpokenResponse("You have no active timers.")
            }
            val first = activeTimers.first()
            val remainingSec = ((first.triggerTimeMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            val minutes = remainingSec / 60
            val seconds = remainingSec % 60
            return CommandInterpreter.Result.DirectSpokenResponse("There are $minutes minutes and $seconds seconds remaining on your ${first.title}.")
        }

        // 2. Cancel timer
        if (q.contains("cancel") && q.contains("timer")) {
            cancelAllTimers()
            return CommandInterpreter.Result.DirectSpokenResponse("All active timers have been cancelled.")
        }

        // 3. Extract duration in minutes
        val pattern = Pattern.compile("(\\d+)\\s*(?:minute|min|m)")
        val matcher = pattern.matcher(q)
        if (matcher.find()) {
            val minutes = matcher.group(1)?.toIntOrNull() ?: 25
            val title = if (q.contains("physics")) "Physics Timer" else if (q.contains("study")) "Study Timer" else "Timer"
            startTimer(minutes, title)
            return CommandInterpreter.Result.DirectSpokenResponse("$minutes-minute $title started.")
        }

        // 4. Default 25 min Pomodoro / Study session if unspecified
        if (q.contains("timer")) {
            startTimer(25, "25-minute timer")
            return CommandInterpreter.Result.DirectSpokenResponse("25-minute timer started.")
        }

        return CommandInterpreter.Result.DirectSpokenResponse("I can set timers for minutes or alarms for specific times.")
    }

    fun startStudyTimer(minutes: Int) {
        startTimer(minutes, "Study Session Timer")
    }

    fun startTimer(minutes: Int, title: String) {
        val timerId = (System.currentTimeMillis() % 10000).toInt()
        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val intent = Intent(context, JarvisAlarmReceiver::class.java).apply {
            putExtra("TIMER_TITLE", title)
            putExtra("TIMER_MESSAGE", "Sir, your $minutes-minute $title has finished.")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timerId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        activeTimers.add(ActiveTimer(timerId, title, minutes, triggerTime))
    }

    fun cancelAllTimers() {
        for (timer in activeTimers) {
            val intent = Intent(context, JarvisAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                timer.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
        activeTimers.clear()
    }
}
