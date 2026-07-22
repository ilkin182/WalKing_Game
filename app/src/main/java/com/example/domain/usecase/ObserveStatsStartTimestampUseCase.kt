package com.example.domain.usecase

import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow

class ObserveStatsStartTimestampUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(): Flow<Long> = repository.statsStartTimestamp
}
