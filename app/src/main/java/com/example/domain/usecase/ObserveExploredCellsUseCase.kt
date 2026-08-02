package com.example.domain.usecase

import com.example.domain.model.ExploredCell
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The player's whole exploration history, cell by cell, with how completely each one is revealed.
 *
 * This replaced an addresses-only flow once the fog needed per-cell levels: two flows over the same
 * Room table would have meant the map's fog and the map's grid reacting to the same change at
 * slightly different times.
 */
class ObserveExploredCellsUseCase(private val repository: StompedHexRepository) {
    operator fun invoke(): Flow<List<ExploredCell>> =
        repository.stompedHexes
            .map { hexes -> hexes.map { it.toExploredCell() } }
            // A Room query failure here would otherwise crash the collecting coroutine
            // (stateIn's WhileSubscribed sharing has no built-in recovery); degrade instead.
            .catch { emit(emptyList()) }
}
