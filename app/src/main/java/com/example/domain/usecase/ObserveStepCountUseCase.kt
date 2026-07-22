package com.example.domain.usecase

import com.example.domain.repository.StepCounterRepository
import kotlinx.coroutines.flow.Flow

class ObserveStepCountUseCase(private val repository: StepCounterRepository) {
    operator fun invoke(): Flow<Int> = repository.stepCount
}
