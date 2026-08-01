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
 *
 * When a [coverageCenter] is supplied, the requested area is first clipped to the
 * [HexGridConfig.COVERAGE_RADIUS_METERS] (100 km) region around it: the whole area within that
 * radius gets divided into cells as the player pans around it, and panning past it stops the grid
 * rather than tiling the rest of the planet.
 */
class GetGridCellsInBoundsUseCase(private val hexGridEngine: HexGridEngine) {
    operator fun invoke(
        bounds: List<Coordinate>,
        stompedAddresses: Set<String>,
        coverageCenter: Coordinate? = null
    ): List<GridCell> {
        val area = if (coverageCenter == null) {
            bounds
        } else {
            HexGridConfig.clipToCoverage(bounds, coverageCenter.lat, coverageCenter.lng)
                ?: return emptyList()
        }

        return try {
            hexGridEngine.polygonToCells(area, HexGridConfig.RESOLUTION).map { cellAddress ->
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
