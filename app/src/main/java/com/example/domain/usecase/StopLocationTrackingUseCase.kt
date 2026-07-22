package com.example.domain.usecase

import com.example.domain.repository.LocationRepository

class StopLocationTrackingUseCase(private val repository: LocationRepository) {
    operator fun invoke() {
        repository.stopUpdates()
    }
}
