package com.example.domain.engine

import com.example.domain.model.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackHexGridEngineTest {
    private val engine = FallbackHexGridEngine()

    @Test
    fun `latLngToCellAddress is stable for the same coordinate`() {
        val first = engine.latLngToCellAddress(40.4093, 49.8671, 11)
        val second = engine.latLngToCellAddress(40.4093, 49.8671, 11)

        assertEquals(first, second)
    }

    @Test
    fun `cellToBoundary returns a hexagon with the cell center roughly in the middle`() {
        val address = engine.latLngToCellAddress(40.4093, 49.8671, 11)
        val boundary = engine.cellToBoundary(address)

        assertEquals(6, boundary.size)
        val centerLat = boundary.sumOf { it.lat } / boundary.size
        val centerLng = boundary.sumOf { it.lng } / boundary.size
        assertTrue(Math.abs(centerLat - 40.4093) < 0.01)
        assertTrue(Math.abs(centerLng - 49.8671) < 0.01)
    }

    @Test
    fun `polygonToCells covers a bounding box wider than a single cell`() {
        val corners = listOf(
            Coordinate(40.412, 49.864),
            Coordinate(40.412, 49.870),
            Coordinate(40.406, 49.870),
            Coordinate(40.406, 49.864)
        )

        val cells = engine.polygonToCells(corners, 11)

        // A box spanning several hundred meters should yield well more than one hex cell,
        // proving the grid isn't limited to a fixed radius around a single point.
        assertTrue(cells.size > 10)
    }

    @Test
    fun `polygonToCells includes the cell addressed at the polygon center`() {
        val centerLat = 40.409
        val centerLng = 49.867
        val delta = 0.002
        val corners = listOf(
            Coordinate(centerLat + delta, centerLng - delta),
            Coordinate(centerLat + delta, centerLng + delta),
            Coordinate(centerLat - delta, centerLng + delta),
            Coordinate(centerLat - delta, centerLng - delta)
        )

        val centerAddress = engine.latLngToCellAddress(centerLat, centerLng, 11)
        val cells = engine.polygonToCells(corners, 11)

        assertTrue(cells.contains(centerAddress))
    }

    @Test
    fun `polygonToCells never returns empty even for a huge zoomed-out bounding box`() {
        // A box spanning several degrees (city/country scale) would naively expand to millions
        // of candidate cells; the engine should clamp to a capped window around the requested
        // area rather than giving up and returning nothing.
        val corners = listOf(
            Coordinate(41.0, 48.0),
            Coordinate(41.0, 51.0),
            Coordinate(39.0, 51.0),
            Coordinate(39.0, 48.0)
        )

        val cells = engine.polygonToCells(corners, 11)

        assertTrue(cells.isNotEmpty())
    }

    @Test
    fun `gridDisk with radius 1 returns the center plus its six neighbors`() {
        val neighbors = engine.gridDisk("fb_0_0", 1)

        assertEquals(7, neighbors.size)
        assertTrue(neighbors.contains("fb_0_0"))
    }
}
