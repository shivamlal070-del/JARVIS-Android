package com.jarvis.assistant.core.study

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PomodoroState {
    IDLE,
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
    PAUSED,
    COMPLETED
}

/**
 * PomodoroManager manages study/break cycles with native timer intervals,
 * speech notifications, and auto-progression.
 */
class PomodoroManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    var focusDurationMinutes = 25
    var shortBreakDurationMinutes = 5
    var longBreakDurationMinutes = 15
    var cyclesBeforeLongBreak = 4
    var isAutomaticMusicEnabled = true
    var isAutomaticContinuationEnabled = true
    var isStrictStudyModeEnabled = false

    private val _pomodoroState = MutableStateFlow(PomodoroState.IDLE)
    val pomodoroState: StateFlow<PomodoroState> = _pomodoroState.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(25 * 60)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _currentCycle = MutableStateFlow(1)
    val currentCycle: StateFlow<Int> = _currentCycle.asStateFlow()

    private val _completedCycles = MutableStateFlow(0)
    val completedCycles: StateFlow<Int> = _completedCycles.asStateFlow()

    private var timerJob: Job? = null
    private var stateBeforePause = PomodoroState.IDLE

    var onPhaseTransition: ((from: PomodoroState, to: PomodoroState, message: String) -> Unit)? = null

    fun startPomodoro(customFocusMinutes: Int? = null) {
        val focusMins = customFocusMinutes ?: focusDurationMinutes
        focusDurationMinutes = focusMins
        _currentCycle.value = 1
        _completedCycles.value = 0
        transitionToState(PomodoroState.FOCUS, focusMins * 60)
    }

    fun pausePomodoro() {
        if (_pomodoroState.value == PomodoroState.FOCUS ||
            _pomodoroState.value == PomodoroState.SHORT_BREAK ||
            _pomodoroState.value == PomodoroState.LONG_BREAK
        ) {
            stateBeforePause = _pomodoroState.value
            _pomodoroState.value = PomodoroState.PAUSED
            timerJob?.cancel()
        }
    }

    fun resumePomodoro() {
        if (_pomodoroState.value == PomodoroState.PAUSED) {
            _pomodoroState.value = stateBeforePause
            startCountdownTimer()
        }
    }

    fun stopPomodoro() {
        timerJob?.cancel()
        _pomodoroState.value = PomodoroState.IDLE
        _remainingSeconds.value = focusDurationMinutes * 60
    }

    fun skipBreak() {
        if (_pomodoroState.value == PomodoroState.SHORT_BREAK || _pomodoroState.value == PomodoroState.LONG_BREAK) {
            timerJob?.cancel()
            advanceToNextFocusCycle()
        }
    }

    fun giveCustomBreak(minutes: Int) {
        timerJob?.cancel()
        transitionToState(PomodoroState.SHORT_BREAK, minutes * 60)
    }

    private fun transitionToState(newState: PomodoroState, seconds: Int) {
        val oldState = _pomodoroState.value
        _pomodoroState.value = newState
        _remainingSeconds.value = seconds
        startCountdownTimer()

        val notificationMsg = when (newState) {
            PomodoroState.FOCUS -> "Focus session ${_currentCycle.value} of $cyclesBeforeLongBreak started. ${seconds / 60} minutes remaining."
            PomodoroState.SHORT_BREAK -> "Focus complete. Time for a ${seconds / 60}-minute break."
            PomodoroState.LONG_BREAK -> "Congratulations! Cycle set complete. Enjoy your ${seconds / 60}-minute long break."
            PomodoroState.COMPLETED -> "All study cycles completed."
            else -> ""
        }
        onPhaseTransition?.invoke(oldState, newState, notificationMsg)
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000L)
                _remainingSeconds.value -= 1
            }
            handleTimerExpiry()
        }
    }

    private fun handleTimerExpiry() {
        when (_pomodoroState.value) {
            PomodoroState.FOCUS -> {
                _completedCycles.value += 1
                if (_completedCycles.value >= cyclesBeforeLongBreak) {
                    transitionToState(PomodoroState.LONG_BREAK, longBreakDurationMinutes * 60)
                } else {
                    transitionToState(PomodoroState.SHORT_BREAK, shortBreakDurationMinutes * 60)
                }
            }
            PomodoroState.SHORT_BREAK -> {
                if (isAutomaticContinuationEnabled) {
                    advanceToNextFocusCycle()
                } else {
                    _pomodoroState.value = PomodoroState.PAUSED
                }
            }
            PomodoroState.LONG_BREAK -> {
                _pomodoroState.value = PomodoroState.COMPLETED
                onPhaseTransition?.invoke(PomodoroState.LONG_BREAK, PomodoroState.COMPLETED, "Pomodoro study session completed.")
            }
            else -> {}
        }
    }

    private fun advanceToNextFocusCycle() {
        _currentCycle.value += 1
        transitionToState(PomodoroState.FOCUS, focusDurationMinutes * 60)
    }
}
