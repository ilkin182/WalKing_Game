package com.example.domain.usecase

import com.example.domain.model.LeaderboardEntry
import com.example.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow

class ObserveLeaderboardUseCase(private val repository: LeaderboardRepository) {
    operator fun invoke(countryCode: String): Flow<List<LeaderboardEntry>> =
        repository.observeCountry(countryCode)
}

/**
 * Puts the player's current figures on their country's board.
 *
 * Called whenever those figures change while the board is being watched, rather than on a timer:
 * a player who claims a cell and opens the ranking expects the cell to be counted.
 */
class PublishLeaderboardEntryUseCase(private val repository: LeaderboardRepository) {
    suspend operator fun invoke(entry: LeaderboardEntry) = repository.publish(entry)
}
