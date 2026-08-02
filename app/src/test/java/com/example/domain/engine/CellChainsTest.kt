package com.example.domain.engine

import com.example.domain.model.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The chains are measured against a real hexagonal layout rather than a hand-drawn adjacency map:
 * the whole point of the algorithm is telling the six directions apart from the geometry, so a fake
 * with made-up centres would test nothing.
 */
class CellChainsTest {

    private val engine = FallbackHexGridEngine()

    private fun run(cells: Set<String>): Int = CellChains.longestStraightRun(
        cells = cells,
        neighborsOf = { cell -> engine.gridDisk(cell, 1).filter { it != cell } },
        centerOf = ::centerOf
    )

    private fun centerOf(cellId: String): Coordinate? {
        val corners = engine.cellToBoundary(cellId)
        if (corners.isEmpty()) return null
        return Coordinate(corners.sumOf { it.lat } / corners.size, corners.sumOf { it.lng } / corners.size)
    }

    /** Cells laid out along one axial direction, which is one of the grid's three axes. */
    private fun line(length: Int, stepQ: Int, stepR: Int, fromQ: Int = 0, fromR: Int = 0): Set<String> =
        (0 until length).mapTo(mutableSetOf()) { "fb_${fromQ + it * stepQ}_${fromR + it * stepR}" }

    @Test
    fun `nothing claimed is no line at all`() {
        assertEquals(0, run(emptySet()))
    }

    @Test
    fun `a single cell is a line of one`() {
        assertEquals(1, run(setOf("fb_0_0")))
    }

    @Test
    fun `cells in a row along an axis count as one line`() {
        assertEquals(20, run(line(length = 20, stepQ = 1, stepR = 0)))
    }

    @Test
    fun `each of the three axes is recognised`() {
        // The three axes of a hexagon, each taken in one direction.
        assertEquals(7, run(line(length = 7, stepQ = 1, stepR = 0)))
        assertEquals(7, run(line(length = 7, stepQ = 0, stepR = 1)))
        assertEquals(7, run(line(length = 7, stepQ = 1, stepR = -1)))
    }

    @Test
    fun `a line is counted across the cell it passes through, not only ahead of it`() {
        // Five cells either side of the origin: the run forwards and the run backwards are both 6,
        // and the line through the middle is 11 rather than 6.
        val cells = line(length = 6, stepQ = 1, stepR = 0) +
            line(length = 6, stepQ = -1, stepR = 0)

        assertEquals(11, run(cells))
    }

    @Test
    fun `a gap breaks the line`() {
        val cells = line(length = 20, stepQ = 1, stepR = 0) - "fb_8_0"

        // The longer of the two remaining pieces, not the twenty cells as a whole.
        assertEquals(11, run(cells))
    }

    @Test
    fun `a bend is not a straight line`() {
        // Ten cells east, then ten cells off along another axis: the longest straight part is the
        // ten of one arm plus the corner cell shared with the other.
        val cells = line(length = 10, stepQ = 1, stepR = 0) +
            line(length = 10, stepQ = 0, stepR = 1, fromQ = 9, fromR = 0)

        assertEquals(10, run(cells))
    }

    @Test
    fun `a blob of cells is not a long line`() {
        // Everything within two rings of the origin: nineteen cells, but the widest straight line
        // across it is only five.
        val blob = engine.gridDisk("fb_0_0", 2).toSet()

        assertEquals(5, run(blob))
    }

    @Test
    fun `cells whose geometry cannot be resolved take no part`() {
        val cells = line(length = 5, stepQ = 1, stepR = 0) + "not-a-cell"

        assertEquals(5, run(cells))
    }
}
