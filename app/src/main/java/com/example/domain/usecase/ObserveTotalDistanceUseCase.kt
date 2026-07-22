package com.example.domain.usecase

import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow

class ObserveTotalDistanceUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(): Flow<Double> = repository.totalDistanceWalked
}
