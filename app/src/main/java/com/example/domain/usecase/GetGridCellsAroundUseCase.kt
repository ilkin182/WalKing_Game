package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.GridCell

/**
 * Computes hexagonal grid cells around a center latitude/longitude for map rendering.
 */
class GetGridCellsAroundUseCase(private val hexGridEngine: HexGridEngine) {
    operator fun invoke(
        lat: Double,
        lng: Double,
        stompedAddresses: Set<String>,
        radiusSteps: Int = 5
    ): List<GridCell> {
        return try {
            val centerCellAddress = hexGridEngine.latLngToCellAddress(lat, lng, RESOLUTION)
            val cellsInRing = hexGridEngine.gridDisk(centerCellAddress, radiusSteps)

            cellsInRing.map { cellAddress ->
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

    private companion object {
        const val RESOLUTION = 11
    }
}
