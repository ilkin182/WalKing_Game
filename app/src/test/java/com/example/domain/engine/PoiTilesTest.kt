package com.example.domain.engine

import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiTilesTest {

    @Test
    fun `positions in the same square share a key`() {
        val a = PoiTiles.keyOf(40.3771, 49.8321)
        val b = PoiTiles.keyOf(40.3799, 49.8399)

        assertEquals(a, b)
    }

    @Test
    fun `positions in neighbouring squares do not`() {
        assertNotEquals(PoiTiles.keyOf(40.3771, 49.8321), PoiTiles.keyOf(40.3871, 49.8321))
        assertNotEquals(PoiTiles.keyOf(40.3771, 49.8321), PoiTiles.keyOf(40.3771, 49.8421))
    }

    /**
     * The whole cache turns on this. A key that drifted with the arithmetic that produced the
     * coordinate would make the app re-fetch squares it already has, which is the one thing the
     * scheme exists to prevent.
     */
    @Test
    fun `a coordinate reached by different arithmetic keys the same square`() {
        val direct = PoiTiles.keyOf(40.37, 49.83)
        val summed = PoiTiles.keyOf(40.0 + 0.37, 49.0 + 0.83)
        val stepped = PoiTiles.keyOf(40.36 + 0.01, 49.82 + 0.01)

        assertEquals(direct, summed)
        assertEquals(direct, stepped)
    }

    @Test
    fun `keys work south and west of zero`() {
        val key = PoiTiles.keyOf(-33.865, -70.665)
        val bounds = PoiTiles.boundsOf(key)!!

        assertTrue(bounds.contains(-33.865, -70.665))
    }

    @Test
    fun `bounds of a key are one tile wide and contain the position that produced it`() {
        val bounds = PoiTiles.boundsOf(PoiTiles.keyOf(40.3771, 49.8321))!!

        assertEquals(PoiTiles.TILE_DEGREES, bounds.north - bounds.south, 1e-9)
        assertEquals(PoiTiles.TILE_DEGREES, bounds.east - bounds.west, 1e-9)
        assertTrue(bounds.contains(40.3771, 49.8321))
    }

    @Test
    fun `a key that did not come from keyOf has no bounds`() {
        assertNull(PoiTiles.boundsOf("not-a-key"))
        assertNull(PoiTiles.boundsOf("4037"))
        assertNull(PoiTiles.boundsOf("4037:west"))
    }

    @Test
    fun `the ring around a position is nine tiles including its own`() {
        val keys = PoiTiles.keysAround(40.3771, 49.8321)

        assertEquals(9, keys.size)
        assertEquals(9, keys.distinct().size)
        assertTrue(PoiTiles.keyOf(40.3771, 49.8321) in keys)
    }

    @Test
    fun `a box spanning several tiles is covered by all of them`() {
        val box = GeoBounds(north = 40.40, south = 40.37, east = 49.85, west = 49.83)

        val keys = PoiTiles.keysCovering(box)

        // Four rows of latitude by three columns of longitude, inclusive at both ends.
        assertEquals(12, keys.size)
        assertTrue(PoiTiles.keyOf(Coordinate(40.375, 49.835)) in keys)
        assertTrue(PoiTiles.keyOf(Coordinate(40.395, 49.845)) in keys)
    }

    @Test
    fun `a box inside one tile is covered by that tile alone`() {
        val box = GeoBounds(north = 40.3779, south = 40.3771, east = 49.8329, west = 49.8321)

        assertEquals(listOf(PoiTiles.keyOf(40.3775, 49.8325)), PoiTiles.keysCovering(box))
    }
}
