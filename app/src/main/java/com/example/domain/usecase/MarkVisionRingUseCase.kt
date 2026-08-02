package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.ExploredCell
import com.example.domain.repository.StompedHexRepository

/**
 * Partially reveals the ring of cells around one the player has been standing in, simulating the
 * distance they can see from where they are.
 *
 * Only ever *adds* exploration: cells already known at this level or better are filtered out before
 * the repository is touched at all, so a stationary phone stops writing to the database entirely
 * once its surroundings are recorded.
 */
class MarkVisionRingUseCase(
    private val repository: StompedHexRepository,
    private val hexGridEngine: HexGridEngine
) {
    /**
     * @param centerCellId the cell the player has been dwelling in.
     * @param knownLevels the exploration level already recorded per cell, so nothing is rewritten.
     * @return the cells that were newly revealed, for the UI's reveal animation.
     */
    suspend operator fun invoke(
        centerCellId: String,
        neighborhood: String?,
        knownLevels: Map<String, Float>,
        level: Float = ExploredCell.LEVEL_VISION
    ): List<String> {
        val ring = try {
            hexGridEngine.gridDisk(centerCellId, VISION_RADIUS_CELLS)
        } catch (e: Exception) {
            // A grid engine that cannot resolve the address is not worth crashing a location update
            // over - the player simply gets no vision ring for this dwell.
            return emptyList()
        }

        val newCells = ring
            .filter { it != centerCellId }
            .filter { (knownLevels[it] ?: 0f) < level }

        if (newCells.isEmpty()) return emptyList()

        repository.markPartiallyExplored(newCells, level, neighborhood)
        return newCells
    }

    private companion object {
        /**
         * One ring out from the centre.
         *
         * The epic says "the 8 neighbouring cells", which is the count for a square grid. This grid
         * is hexagonal, so one ring is 6 cells - the same "everything I can see from here" shape,
         * which is what the requirement is actually after.
         */
        const val VISION_RADIUS_CELLS = 1
    }
}
