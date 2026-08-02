package com.example.ui.map.fog

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.GeoBounds

/** One explored cell with its geometry already resolved, ready to be punched out of a fog tile. */
data class ExploredCellGeometry(
    val cellId: String,
    val corners: List<Coordinate>,
    val explorationLevel: Float,
    val bounds: GeoBounds
)

/**
 * A coarse spatial index over the explored cells, so rendering a fog tile costs "the cells near this
 * tile" rather than "every cell the player has ever walked on".
 *
 * Without it, drawing the ~12 tiles of a viewport with 1000+ explored cells means 12,000 geometry
 * resolutions and bbox tests per pan; the epic's 60 fps target does not survive that. Cells are
 * bucketed into [BUCKET_DEGREES] squares (~1.1 km), so a tile query touches a handful of buckets.
 *
 * The index is immutable: a new one is built (off the main thread) whenever the explored set
 * changes, which is also what [version] stamps into the tile cache keys.
 */
class ExploredCellIndex private constructor(
    private val buckets: Map<Long, List<ExploredCellGeometry>>,
    val version: Long,
    val size: Int
) {

    /** The explored cells whose bounding box overlaps [bounds]. Order is unspecified. */
    fun query(bounds: GeoBounds): List<ExploredCellGeometry> {
        if (size == 0) return emptyList()

        val minLat = bucketIndex(bounds.south)
        val maxLat = bucketIndex(bounds.north)
        val minLng = bucketIndex(bounds.west)
        val maxLng = bucketIndex(bounds.east)

        // A cell straddling a bucket edge is stored in every bucket it touches, so de-duplicate on
        // the way out. Keyed by cellId rather than by the geometry itself: the value type holds a
        // corner list, and comparing those structurally on every hit would undo the point of the
        // index.
        val hits = LinkedHashMap<String, ExploredCellGeometry>()
        for (latIdx in minLat..maxLat) {
            for (lngIdx in minLng..maxLng) {
                val bucket = buckets[key(latIdx, lngIdx)] ?: continue
                bucket.forEach { cell ->
                    if (cell.bounds.intersects(bounds)) hits.putIfAbsent(cell.cellId, cell)
                }
            }
        }
        return hits.values.toList()
    }

    /** True when nothing has been explored, so the whole map is under plain fog. */
    fun isEmpty(): Boolean = size == 0

    companion object {
        /** ~1.1 km at the equator: a few dozen cells per bucket at the grid's ~40 m resolution. */
        const val BUCKET_DEGREES = 0.01

        val EMPTY = ExploredCellIndex(emptyMap(), version = 0L, size = 0)

        /**
         * Resolves every cell's geometry once and buckets it.
         *
         * [resolveCorners] is the grid engine's `cellToBoundary`, passed in rather than depended on
         * so this stays testable without a grid engine. Cells it cannot resolve (a stale address
         * from an older grid, say) are skipped instead of failing the whole build.
         */
        fun build(
            cells: Collection<ExploredCell>,
            version: Long,
            resolveCorners: (String) -> List<Coordinate>
        ): ExploredCellIndex {
            if (cells.isEmpty()) return ExploredCellIndex(emptyMap(), version, 0)

            val buckets = HashMap<Long, MutableList<ExploredCellGeometry>>()
            var stored = 0

            cells.forEach { cell ->
                val corners = runCatching { resolveCorners(cell.cellId) }.getOrNull().orEmpty()
                val bounds = GeoBounds.of(corners) ?: return@forEach

                val geometry = ExploredCellGeometry(
                    cellId = cell.cellId,
                    corners = corners,
                    explorationLevel = ExploredCell.clampLevel(cell.explorationLevel),
                    bounds = bounds
                )
                stored++

                for (latIdx in bucketIndex(bounds.south)..bucketIndex(bounds.north)) {
                    for (lngIdx in bucketIndex(bounds.west)..bucketIndex(bounds.east)) {
                        buckets.getOrPut(key(latIdx, lngIdx)) { mutableListOf() }.add(geometry)
                    }
                }
            }

            return ExploredCellIndex(buckets, version, stored)
        }

        private fun bucketIndex(degrees: Double): Int = Math.floor(degrees / BUCKET_DEGREES).toInt()

        private fun key(latIdx: Int, lngIdx: Int): Long =
            (latIdx.toLong() shl 32) or (lngIdx.toLong() and 0xFFFFFFFFL)
    }
}
