package com.example.domain.usecase

import com.example.domain.repository.LocationRepository

class StartLocationTrackingUseCase(private val repository: LocationRepository) {
    operator fun invoke(intervalMs: Long) {
        repository.startUpdates(intervalMs)
    }
}
