package com.example.ui.map.fog

import com.example.domain.model.GeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    @Test
    fun `zoom zero is a single tile covering the whole world`() {
        assertEquals(1, TileMath.tilesPerAxis(0))

        val bounds = TileMath.tileBounds(0, 0, 0)
        assertEquals(-180.0, bounds.west, 1e-9)
        assertEquals(180.0, bounds.east, 1e-9)
        assertEquals(TileMath.MAX_LATITUDE, bounds.north, 1e-6)
        assertEquals(-TileMath.MAX_LATITUDE, bounds.south, 1e-6)
    }

    @Test
    fun `the origin sits on the corner of the four middle tiles at zoom one`() {
        assertEquals(1.0, TileMath.lngToTileX(0.0, 1), 1e-9)
        assertEquals(1.0, TileMath.latToTileY(0.0, 1), 1e-9)
    }

    @Test
    fun `tile x and y round-trip through their inverses`() {
        val zoom = 14
        val lat = 40.4093
        val lng = 49.8671

        assertEquals(lng, TileMath.tileXToLng(TileMath.lngToTileX(lng, zoom), zoom), 1e-9)
        assertEquals(lat, TileMath.tileYToLat(TileMath.latToTileY(lat, zoom), zoom), 1e-9)
    }

    @Test
    fun `tile y grows southwards`() {
        val zoom = 10
        val north = TileMath.latToTileY(60.0, zoom)
        val south = TileMath.latToTileY(20.0, zoom)

        assertTrue("expected the northern latitude to have the smaller tile index", north < south)
    }

    @Test
    fun `a tile's bounds contain the point it was derived from`() {
        val zoom = 16
        val lat = 40.4093
        val lng = 49.8671
        val x = Math.floor(TileMath.lngToTileX(lng, zoom)).toInt()
        val y = Math.floor(TileMath.latToTileY(lat, zoom)).toInt()

        assertTrue(TileMath.tileBounds(zoom, x, y).contains(lat, lng))
    }

    @Test
    fun `adjacent tiles share an edge with no gap`() {
        val zoom = 12
        val left = TileMath.tileBounds(zoom, 100, 50)
        val right = TileMath.tileBounds(zoom, 101, 50)
        val below = TileMath.tileBounds(zoom, 100, 51)

        assertEquals(left.east, right.west, 1e-12)
        assertEquals(left.south, below.north, 1e-12)
    }

    @Test
    fun `tilesCovering returns every tile overlapping the area`() {
        val zoom = 14
        val a = TileMath.tileBounds(zoom, 200, 300)
        val b = TileMath.tileBounds(zoom, 201, 301)
        val span = GeoBounds(north = a.north, south = b.south, east = b.east, west = a.west)

        val tiles = TileMath.tilesCovering(span, zoom)

        assertEquals(4, tiles.size)
        assertTrue(tiles.contains(TileId(zoom, 200, 300)))
        assertTrue(tiles.contains(TileId(zoom, 201, 301)))
    }

    @Test
    fun `tilesCovering clamps to the world instead of asking for tiles that do not exist`() {
        val zoom = 3
        val whole = GeoBounds(north = 89.0, south = -89.0, east = 180.0, west = -180.0)

        val tiles = TileMath.tilesCovering(whole, zoom)

        val max = TileMath.tilesPerAxis(zoom) - 1
        assertTrue(tiles.all { it.x in 0..max && it.y in 0..max })
    }

    @Test
    fun `a point inside a tile projects inside that tile's pixels`() {
        val zoom = 16
        val x = 44000
        val y = 25000
        val bounds = TileMath.tileBounds(zoom, x, y)

        val px = TileMath.pixelXInTile(bounds.centerLng, zoom, x)
        val py = TileMath.pixelYInTile(bounds.centerLat, zoom, y)

        assertEquals(TileMath.TILE_SIZE_PX / 2f, px, 0.5f)
        // Mercator is not linear in latitude, so the mid-latitude is near - not exactly at - the
        // vertical centre. It only has to land inside the tile.
        assertTrue(py > 0f && py < TileMath.TILE_SIZE_PX)
    }

    @Test
    fun `a neighbouring cell projects outside the tile, which is how seams stay seamless`() {
        val zoom = 16
        val x = 44000
        val neighbour = TileMath.tileBounds(zoom, x + 1, 25000)

        assertTrue(TileMath.pixelXInTile(neighbour.centerLng, zoom, x) > TileMath.TILE_SIZE_PX)
    }

    @Test
    fun `ground resolution halves with every zoom level`() {
        val coarse = TileMath.metersPerPixel(15, 0.0)
        val fine = TileMath.metersPerPixel(16, 0.0)

        assertEquals(coarse / 2.0, fine, 1e-6)
    }

    @Test
    fun `ground resolution shrinks away from the equator`() {
        assertTrue(TileMath.metersPerPixel(16, 60.0) < TileMath.metersPerPixel(16, 0.0))
    }

    @Test
    fun `latitudes past the mercator limit are clamped rather than producing infinities`() {
        val y = TileMath.latToTileY(89.9, 10)

        assertTrue(y.isFinite())
        assertTrue(y >= 0.0)
    }
}
