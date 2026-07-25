package com.example.domain.usecase

import com.example.domain.engine.HexGridConfig
import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import com.example.domain.model.GridCell

/**
 * Computes every hex cell whose center falls within the given area (typically the map's visible
 * bounding box), so the rendered grid always covers the whole visible map instead of a
 * fixed-radius disk around a single point. Previously-stomped cells are looked up against
 * [stompedAddresses] (the full persisted history), so explored tiles stay marked as explored no
 * matter where the map is currently centered.
 */
class GetGridCellsInBoundsUseCase(private val hexGridEngine: HexGridEngine) {
    operator fun invoke(bounds: List<Coordinate>, stompedAddresses: Set<String>): List<GridCell> {
        return try {
            hexGridEngine.polygonToCells(bounds, HexGridConfig.RESOLUTION).map { cellAddress ->
                GridCell(
                    address = cellAddress,
                    corners = hexGridEngine.cellToBoundary(cellAddress),
                    isStomped = stompedAddresses.contains(cellAddress)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
