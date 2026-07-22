package com.example.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-component holder for the live step count. StepCounterService is the only writer;
 * StepCounterRepositoryImpl only reads from [stepCount].
 */
object StepCounterState {
    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    fun updateStepCount(steps: Int) {
        _stepCount.value = steps
    }

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }
}
