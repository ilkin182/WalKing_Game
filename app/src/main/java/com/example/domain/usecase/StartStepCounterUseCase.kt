package com.example.domain.usecase

import com.example.domain.repository.StepCounterRepository

class StartStepCounterUseCase(private val repository: StepCounterRepository) {
    operator fun invoke() {
        repository.start()
    }
}
