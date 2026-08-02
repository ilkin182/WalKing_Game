package com.example.domain.usecase

import com.example.domain.engine.HexGridConfig
import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate

/**
 * Direct lookups against the grid: which cell a position falls in, and what shape a cell is.
 *
 * The fog layer needs cell outlines to punch them out of the fog, and the dwell tracker needs the id
 * of the cell the player is standing in - neither has any business knowing which grid engine is in
 * use, and this is the seam between them. Both lookups resolve through [HexGridConfig.RESOLUTION],
 * the same one stomping uses, so the ids always match.
 *
 * Failures come back as empty/null rather than thrown: one unresolvable address (left over from an
 * older grid, say) must not take down the fog layer or a location update.
 */
class GridCellLookupUseCase(private val hexGridEngine: HexGridEngine) {

    fun cornersOf(cellId: String): List<Coordinate> =
        try {
            hexGridEngine.cellToBoundary(cellId)
        } catch (e: Exception) {
            emptyList()
        }

    fun cellIdAt(lat: Double, lng: Double): String? =
        try {
            hexGridEngine.latLngToCellAddress(lat, lng, HexGridConfig.RESOLUTION)
        } catch (e: Exception) {
            null
        }

    /**
     * The six cells touching this one, for the rules that read the shape of what has been claimed.
     * The centre itself is filtered out, so the result is only the ring around it.
     */
    fun neighborsOf(cellId: String): List<String> =
        try {
            hexGridEngine.gridDisk(cellId, 1).filter { it != cellId }
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * A cell's centre, averaged from its corners - the engine exposes the outline, not a centre.
     * Good enough for anything measured at cell scale (a cell is ~40 m across), which is what the
     * elevation lookup and the "how far from home" statistic need.
     */
    fun centerOf(cellId: String): Coordinate? {
        val corners = cornersOf(cellId)
        if (corners.isEmpty()) return null
        return Coordinate(
            lat = corners.sumOf { it.lat } / corners.size,
            lng = corners.sumOf { it.lng } / corners.size
        )
    }
}
