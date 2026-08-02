package com.example.ui.map.grid

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.GridCell
import com.example.ui.map.fog.ExploredCellGeometry

/**
 * Everything the grid overlay needs to draw one viewport, already reduced to the lines that will
 * actually be painted.
 *
 * Built off the main thread ([forViewport]) and handed to [HexGridOverlay] finished, so a frame only
 * ever costs projecting these points - never resolving cell geometry or deduplicating edges.
 *
 * @property walked rings of fully explored cells, filled as one region.
 * @property seen rings of partially explored cells, filled more faintly.
 * @property territoryBorder the outline of walked + seen taken together, with interior seams removed.
 * @property emptyEdges the honeycomb over unwalked ground, one line per shared edge.
 */
data class HexGridGeometry(
    val walked: List<List<Coordinate>>,
    val seen: List<List<Coordinate>>,
    val territoryBorder: List<GridEdge>,
    val emptyEdges: List<GridEdge>
) {
    val isEmpty: Boolean
        get() = walked.isEmpty() && seen.isEmpty() && emptyEdges.isEmpty()

    companion object {
        val EMPTY = HexGridGeometry(emptyList(), emptyList(), emptyList(), emptyList())

        /**
         * Assembles the geometry for one viewport.
         *
         * [explored] comes from the fog layer's index - the corners are already resolved there, so
         * claimed territory costs no grid-engine work at all. [emptyCells] is the honeycomb, and is
         * only worth fetching when the level of detail says it will be drawn; pass an empty list
         * otherwise.
         */
        fun forViewport(
            explored: List<ExploredCellGeometry>,
            emptyCells: List<GridCell>,
            lod: GridLod
        ): HexGridGeometry {
            if (!lod.drawsTerritory) return EMPTY

            val walked = ArrayList<List<Coordinate>>()
            val seen = ArrayList<List<Coordinate>>()
            explored.forEach { cell ->
                if (cell.corners.size < 3) return@forEach
                if (cell.explorationLevel >= ExploredCell.LEVEL_WALKED) {
                    walked.add(cell.corners)
                } else {
                    seen.add(cell.corners)
                }
            }

            return HexGridGeometry(
                walked = walked,
                seen = seen,
                territoryBorder = HexEdges.boundary(walked + seen),
                emptyEdges = if (lod.drawsEmptyCells) {
                    HexEdges.unique(emptyCells.filterNot { it.isStomped }.map { it.corners })
                } else {
                    emptyList()
                }
            )
        }
    }
}
