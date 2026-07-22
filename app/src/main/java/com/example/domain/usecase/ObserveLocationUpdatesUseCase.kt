package com.example.domain.usecase

import com.example.domain.model.GeoLocation
import com.example.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class ObserveLocationUpdatesUseCase(private val repository: LocationRepository) {
    operator fun invoke(): Flow<GeoLocation> = repository.locationUpdates
}
