package com.example.domain.usecase

import com.example.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class ObserveLocationErrorsUseCase(private val repository: LocationRepository) {
    operator fun invoke(): Flow<String> = repository.errors
}
