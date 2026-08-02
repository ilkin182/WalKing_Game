package com.example.domain.usecase

import com.example.data.remote.ElevationApi
import com.example.domain.model.Coordinate
import com.example.domain.repository.ElevationRepository
import com.example.domain.repository.StompedHexRepository

/**
 * Fills in the elevation of cells that do not have one yet, a batch at a time.
 *
 * Runs behind the game rather than in the stomping path: a lookup per cell would be a network round
 * trip every few seconds while walking, where one request covers a hundred cells. Because the queue
 * is simply "cells with no elevation, oldest first", the same pass also backfills everything the
 * player claimed before the column existed - no separate migration job.
 *
 * Returns how many cells it managed to fill, so the caller can keep going while there is work.
 */
class EnrichCellElevationsUseCase(
    private val cells: StompedHexRepository,
    private val elevations: ElevationRepository,
    private val resolveCenter: (String) -> Coordinate?
) {
    suspend operator fun invoke(limit: Int = ElevationApi.MAX_POINTS_PER_REQUEST): Int {
        val pending = cells.cellsMissingElevation(limit)
        if (pending.isEmpty()) return 0

        // Cells whose geometry cannot be resolved are dropped from the batch rather than sent with
        // a placeholder coordinate, which would record a confidently wrong height.
        val located = pending.mapNotNull { id ->
            runCatching { resolveCenter(id) }.getOrNull()?.let { id to it }
        }
        if (located.isEmpty()) return 0

        val heights = elevations.elevations(located.map { it.second })
        if (heights.size != located.size) return 0

        cells.setElevations(located.mapIndexed { i, (id, _) -> id to heights[i] }.toMap())
        return located.size
    }
}
