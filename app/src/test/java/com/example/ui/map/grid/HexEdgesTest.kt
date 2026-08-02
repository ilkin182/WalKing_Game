package com.example.ui.map.grid

import com.example.domain.model.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The edge reduction is what turns a block of claimed cells into one outlined territory. It is
 * shape-agnostic, so these use unit squares: the arithmetic is checkable by hand and the property
 * being tested - shared edges are interior - is the same one hexagons rely on.
 */
class HexEdgesTest {

    /** The square whose south-west corner is at [row]/[col], walked anticlockwise. */
    private fun square(row: Int, col: Int): List<Coordinate> = listOf(
        Coordinate(row.toDouble(), col.toDouble()),
        Coordinate(row.toDouble(), col + 1.0),
        Coordinate(row + 1.0, col + 1.0),
        Coordinate(row + 1.0, col.toDouble())
    )

    @Test
    fun `a lone cell is all boundary`() {
        val ring = square(0, 0)

        assertEquals(4, HexEdges.unique(listOf(ring)).size)
        assertEquals(4, HexEdges.boundary(listOf(ring)).size)
    }

    @Test
    fun `the seam between two neighbours is drawn once and bounds neither`() {
        val cells = listOf(square(0, 0), square(0, 1))

        // 8 edges, one of them shared: 7 distinct lines, 6 of which are on the outside.
        assertEquals(7, HexEdges.unique(cells).size)
        assertEquals(6, HexEdges.boundary(cells).size)
    }

    @Test
    fun `a solid block comes back as its perimeter, not as a grid`() {
        val block = (0..2).flatMap { row -> (0..2).map { col -> square(row, col) } }

        // 3x3 of unit cells: a perimeter of 12 unit edges, and 12 interior seams dropped.
        val boundary = HexEdges.boundary(block)
        assertEquals(12, boundary.size)
        assertEquals(24, HexEdges.unique(block).size)

        // Nothing strictly inside the block survives.
        assertTrue(
            boundary.none { it.from.lat in 0.5..2.5 && it.from.lng in 0.5..2.5 }
        )
    }

    @Test
    fun `neighbours whose shared corners differ in the last bits still share an edge`() {
        // Real grid engines resolve each cell's boundary independently, so a shared vertex can come
        // back a nanodegree apart. Matching exactly would print every interior seam as a border.
        val jitter = 1e-9
        val neighbour = square(0, 1).map { Coordinate(it.lat + jitter, it.lng - jitter) }

        assertEquals(6, HexEdges.boundary(listOf(square(0, 0), neighbour)).size)
    }

    @Test
    fun `degenerate rings are skipped rather than drawn`() {
        val cells = listOf(square(0, 0), emptyList(), listOf(Coordinate(9.0, 9.0)))

        assertEquals(4, HexEdges.unique(cells).size)
        assertEquals(4, HexEdges.boundary(cells).size)
    }

    @Test
    fun `nothing in means nothing out`() {
        assertTrue(HexEdges.unique(emptyList()).isEmpty())
        assertTrue(HexEdges.boundary(emptyList()).isEmpty())
    }
}
