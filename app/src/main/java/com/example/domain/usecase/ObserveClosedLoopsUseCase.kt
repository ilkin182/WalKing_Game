package com.example.domain.usecase

import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow

/** How many closed loops the player has walked, for the "Halqa" achievement. */
class ObserveClosedLoopsUseCase(private val repository: UserStatsRepository) {
    operator fun invoke(): Flow<Int> = repository.closedLoops
}
