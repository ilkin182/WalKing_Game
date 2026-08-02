package com.example.ui.map.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the thresholds that keep the map readable. These are easy to "simplify" away later and the
 * damage only shows up on a real device at city zoom, where an always-on grid covers the whole
 * viewport in a flat mesh of ~40 m cells.
 */
class GridLodTest {

    @Test
    fun `the honeycomb is off at city zoom, where cells are smaller than a line is wide`() {
        listOf(10.0, 13.0, 14.0, 15.9).forEach { zoom ->
            assertFalse("zoom $zoom draws guide lines", GridLod.forZoom(zoom).drawsEmptyCells)
        }
    }

    @Test
    fun `the honeycomb fades in rather than snapping on`() {
        val start = GridLod.forZoom(GridLod.MIN_EMPTY_GRID_ZOOM)
        val middle = GridLod.forZoom(
            (GridLod.MIN_EMPTY_GRID_ZOOM + GridLod.FULL_EMPTY_GRID_ZOOM) / 2
        )
        val full = GridLod.forZoom(GridLod.FULL_EMPTY_GRID_ZOOM)

        assertEquals(0, start.emptyCellAlpha)
        assertTrue(middle.emptyCellAlpha in 1 until GridLod.MAX_EMPTY_CELL_ALPHA)
        assertEquals(GridLod.MAX_EMPTY_CELL_ALPHA, full.emptyCellAlpha)
        assertTrue(middle.emptyStrokeDp in start.emptyStrokeDp..full.emptyStrokeDp)
    }

    @Test
    fun `guide lines never reach full opacity`() {
        // They mark where the next cell begins; they are not meant to compete with the roads.
        assertTrue(GridLod.forZoom(21.0).emptyCellAlpha < 255 / 2)
    }

    @Test
    fun `territory survives far past the zoom the guide lines are dropped at`() {
        val cityZoom = GridLod.forZoom(14.0)

        assertTrue(cityZoom.drawsTerritory)
        assertFalse(cityZoom.drawsEmptyCells)
    }

    @Test
    fun `nothing is drawn once a cell is a couple of pixels across`() {
        val worldZoom = GridLod.forZoom(GridLod.MIN_TERRITORY_ZOOM - 0.1)

        assertEquals(GridLod.HIDDEN, worldZoom)
        assertFalse(worldZoom.drawsTerritory)
    }

    @Test
    fun `the level of detail is monotonic in zoom`() {
        var previous = GridLod.forZoom(1.0)
        generateSequence(1.5) { it + 0.5 }.takeWhile { it <= 22.0 }.forEach { zoom ->
            val current = GridLod.forZoom(zoom)
            assertTrue(
                "alpha dropped between ${zoom - 0.5} and $zoom",
                current.emptyCellAlpha >= previous.emptyCellAlpha
            )
            previous = current
        }
    }
}
