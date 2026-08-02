package com.example.ui.map.grid

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.GeoBounds
import com.example.domain.model.GridCell
import com.example.ui.map.fog.ExploredCellGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexGridGeometryTest {

    private fun ring(row: Int, col: Int): List<Coordinate> = listOf(
        Coordinate(row.toDouble(), col.toDouble()),
        Coordinate(row.toDouble(), col + 1.0),
        Coordinate(row + 1.0, col + 1.0),
        Coordinate(row + 1.0, col.toDouble())
    )

    private fun explored(row: Int, col: Int, level: Float): ExploredCellGeometry {
        val corners = ring(row, col)
        return ExploredCellGeometry("$row:$col", corners, level, GeoBounds.of(corners)!!)
    }

    private fun emptyCell(row: Int, col: Int, stomped: Boolean = false) =
        GridCell("$row:$col", ring(row, col), stomped)

    private val nearZoom = GridLod.forZoom(18.0)
    private val cityZoom = GridLod.forZoom(14.0)

    @Test
    fun `walked and merely seen ground are kept apart so they can be shaded differently`() {
        val geometry = HexGridGeometry.forViewport(
            explored = listOf(
                explored(0, 0, ExploredCell.LEVEL_WALKED),
                explored(0, 1, ExploredCell.LEVEL_VISION)
            ),
            emptyCells = emptyList(),
            lod = nearZoom
        )

        assertEquals(1, geometry.walked.size)
        assertEquals(1, geometry.seen.size)
    }

    @Test
    fun `the border runs round walked and seen ground together, not between them`() {
        val geometry = HexGridGeometry.forViewport(
            explored = listOf(
                explored(0, 0, ExploredCell.LEVEL_WALKED),
                explored(0, 1, ExploredCell.LEVEL_VISION)
            ),
            emptyCells = emptyList(),
            lod = nearZoom
        )

        // One outline round the pair (6 edges), not two separate outlines (8).
        assertEquals(6, geometry.territoryBorder.size)
    }

    @Test
    fun `claimed cells are left out of the honeycomb so nothing is drawn twice`() {
        val geometry = HexGridGeometry.forViewport(
            explored = listOf(explored(0, 0, ExploredCell.LEVEL_WALKED)),
            emptyCells = listOf(emptyCell(0, 0, stomped = true), emptyCell(0, 1)),
            lod = nearZoom
        )

        assertEquals(4, geometry.emptyEdges.size)
    }

    @Test
    fun `zoomed out, the honeycomb is not built even when its cells were supplied`() {
        val geometry = HexGridGeometry.forViewport(
            explored = listOf(explored(0, 0, ExploredCell.LEVEL_WALKED)),
            emptyCells = listOf(emptyCell(0, 1), emptyCell(0, 2)),
            lod = cityZoom
        )

        assertTrue(geometry.emptyEdges.isEmpty())
        // Territory still shows: it is the game state, not a guide line.
        assertEquals(1, geometry.walked.size)
        assertFalse(geometry.isEmpty)
    }

    @Test
    fun `below the territory zoom nothing is built at all`() {
        val geometry = HexGridGeometry.forViewport(
            explored = listOf(explored(0, 0, ExploredCell.LEVEL_WALKED)),
            emptyCells = listOf(emptyCell(0, 1)),
            lod = GridLod.HIDDEN
        )

        assertEquals(HexGridGeometry.EMPTY, geometry)
        assertTrue(geometry.isEmpty)
    }

    @Test
    fun `cells the grid engine could not resolve are skipped instead of drawn as slivers`() {
        val broken = ExploredCellGeometry(
            cellId = "broken",
            corners = listOf(Coordinate(0.0, 0.0), Coordinate(0.0, 1.0)),
            explorationLevel = ExploredCell.LEVEL_WALKED,
            bounds = GeoBounds(1.0, 0.0, 1.0, 0.0)
        )

        val geometry = HexGridGeometry.forViewport(listOf(broken), emptyList(), nearZoom)

        assertTrue(geometry.walked.isEmpty())
        assertTrue(geometry.territoryBorder.isEmpty())
    }
}
