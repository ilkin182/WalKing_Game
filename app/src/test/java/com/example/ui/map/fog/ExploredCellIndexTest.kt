package com.example.ui.map.fog

import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploredCellIndexTest {

    /** A square "cell" of [size] degrees with its south-west corner at (lat, lng). */
    private fun square(lat: Double, lng: Double, size: Double = 0.0004) = listOf(
        Coordinate(lat, lng),
        Coordinate(lat, lng + size),
        Coordinate(lat + size, lng + size),
        Coordinate(lat + size, lng)
    )

    private fun cell(id: String, level: Float = ExploredCell.LEVEL_WALKED) =
        ExploredCell(id, exploredAt = 0L, explorationLevel = level)

    private fun indexOf(vararg cells: Pair<String, List<Coordinate>>): ExploredCellIndex {
        val geometry = cells.toMap()
        return ExploredCellIndex.build(
            cells.map { cell(it.first) },
            version = 1L
        ) { id -> geometry[id].orEmpty() }
    }

    @Test
    fun `an empty index reports itself empty and matches nothing`() {
        val index = ExploredCellIndex.EMPTY

        assertTrue(index.isEmpty())
        assertTrue(index.query(GeoBounds(1.0, 0.0, 1.0, 0.0)).isEmpty())
    }

    @Test
    fun `finds a cell inside the queried area`() {
        val index = indexOf("a" to square(40.4093, 49.8671))

        val hits = index.query(GeoBounds(north = 40.41, south = 40.40, east = 49.87, west = 49.86))

        assertEquals(listOf("a"), hits.map { it.cellId })
    }

    @Test
    fun `ignores a cell outside the queried area`() {
        val index = indexOf("far" to square(41.0, 50.0))

        val hits = index.query(GeoBounds(north = 40.41, south = 40.40, east = 49.87, west = 49.86))

        assertTrue(hits.isEmpty())
    }

    @Test
    fun `a cell touching the edge of the query still counts`() {
        val index = indexOf("edge" to square(40.4093, 49.8671, size = 0.0004))
        val cellBounds = GeoBounds.of(square(40.4093, 49.8671, size = 0.0004))!!

        // A query whose western edge lands exactly on the cell's eastern edge: the cell overlaps by
        // a hairline and its fog-clearing still bleeds into the query area.
        val hits = index.query(
            GeoBounds(
                north = cellBounds.north,
                south = cellBounds.south,
                east = cellBounds.east + 0.001,
                west = cellBounds.east
            )
        )

        assertEquals(listOf("edge"), hits.map { it.cellId })
    }

    @Test
    fun `a cell straddling a bucket boundary is returned exactly once`() {
        // ExploredCellIndex buckets at 0.01 degrees; this square sits across one of those seams and
        // is therefore stored in two buckets.
        val straddling = square(lat = 40.40999, lng = 49.86999, size = 0.0004)
        val index = indexOf("straddler" to straddling)

        val hits = index.query(GeoBounds(north = 40.42, south = 40.40, east = 49.88, west = 49.86))

        assertEquals(1, hits.size)
        assertEquals("straddler", hits.single().cellId)
    }

    @Test
    fun `a query spanning many buckets returns every cell once`() {
        val index = indexOf(
            "a" to square(40.400, 49.860),
            "b" to square(40.415, 49.875),
            "c" to square(40.430, 49.890)
        )

        val hits = index.query(GeoBounds(north = 40.44, south = 40.39, east = 49.90, west = 49.85))

        assertEquals(setOf("a", "b", "c"), hits.map { it.cellId }.toSet())
        assertEquals(3, hits.size)
    }

    @Test
    fun `keeps each cell's exploration level`() {
        val geometry = mapOf("walked" to square(40.40, 49.86), "seen" to square(40.401, 49.86))
        val index = ExploredCellIndex.build(
            listOf(cell("walked"), cell("seen", ExploredCell.LEVEL_VISION)),
            version = 7L
        ) { geometry[it].orEmpty() }

        val hits = index.query(GeoBounds(north = 40.41, south = 40.39, east = 49.87, west = 49.85))
            .associateBy { it.cellId }

        assertEquals(ExploredCell.LEVEL_WALKED, hits.getValue("walked").explorationLevel)
        assertEquals(ExploredCell.LEVEL_VISION, hits.getValue("seen").explorationLevel)
        assertEquals(7L, index.version)
    }

    @Test
    fun `levels outside 0 to 1 are clamped`() {
        val geometry = mapOf("odd" to square(40.40, 49.86))
        val index = ExploredCellIndex.build(listOf(cell("odd", 4.2f)), 1L) { geometry[it].orEmpty() }

        val hit = index.query(GeoBounds(40.41, 40.39, 49.87, 49.85)).single()

        assertEquals(1.0f, hit.explorationLevel, 1e-6f)
    }

    @Test
    fun `a cell whose geometry cannot be resolved is skipped, not fatal`() {
        val index = ExploredCellIndex.build(
            listOf(cell("good"), cell("stale")),
            version = 1L
        ) { id -> if (id == "good") square(40.40, 49.86) else emptyList() }

        assertEquals(1, index.size)
        assertFalse(index.isEmpty())
        assertEquals(listOf("good"), index.query(GeoBounds(40.41, 40.39, 49.87, 49.85)).map { it.cellId })
    }

    @Test
    fun `a geometry lookup that throws does not take the whole index down`() {
        val index = ExploredCellIndex.build(
            listOf(cell("good"), cell("explodes")),
            version = 1L
        ) { id ->
            if (id == "explodes") throw IllegalStateException("bad address") else square(40.40, 49.86)
        }

        assertEquals(1, index.size)
    }
}
