package com.example.ui.map.fog

import com.example.domain.model.GeoBounds

/**
 * Web Mercator slippy-map tile math, the same scheme osmdroid and every XYZ tile server use.
 *
 * A zoom level `z` splits the world into `2^z` tiles per axis. Tile (0,0) is the north-west corner.
 * [TILE_SIZE_PX] only sets how many pixels a tile is *rendered* at - a 512 px tile covers exactly
 * the same ground as a 256 px one at the same zoom, just at twice the resolution, which is what
 * keeps the fog's cleared edges smooth on high-density screens.
 *
 * Pure math with no Android dependency, so it is unit-testable on the JVM.
 */
object TileMath {

    /** Fog tiles are rendered at 512 px, per the epic. */
    const val TILE_SIZE_PX = 512

    /** Web Mercator is undefined at the poles; this is the standard cut-off. */
    const val MAX_LATITUDE = 85.0511287798066

    fun tilesPerAxis(zoom: Int): Int = 1 shl zoom

    /** Fractional tile X for a longitude - the integer part is the tile, the rest is the offset in it. */
    fun lngToTileX(lng: Double, zoom: Int): Double =
        (lng + 180.0) / 360.0 * tilesPerAxis(zoom)

    /** Fractional tile Y for a latitude. Latitudes beyond [MAX_LATITUDE] are clamped, not wrapped. */
    fun latToTileY(lat: Double, zoom: Int): Double {
        val n = tilesPerAxis(zoom)
        val clamped = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val latRad = Math.toRadians(clamped)
        val mercator = Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad))
        // At exactly MAX_LATITUDE the arithmetic lands on 0 (or a hair either side of it), so the
        // result is clamped rather than handed back as a very small negative number.
        return ((1.0 - mercator / Math.PI) / 2.0 * n).coerceIn(0.0, n.toDouble())
    }

    fun tileXToLng(tileX: Double, zoom: Int): Double =
        tileX / tilesPerAxis(zoom) * 360.0 - 180.0

    fun tileYToLat(tileY: Double, zoom: Int): Double {
        val n = Math.PI * (1.0 - 2.0 * tileY / tilesPerAxis(zoom))
        return Math.toDegrees(Math.atan(Math.sinh(n)))
    }

    /** The ground a whole tile covers. */
    fun tileBounds(zoom: Int, x: Int, y: Int): GeoBounds = GeoBounds(
        north = tileYToLat(y.toDouble(), zoom),
        south = tileYToLat((y + 1).toDouble(), zoom),
        west = tileXToLng(x.toDouble(), zoom),
        east = tileXToLng((x + 1).toDouble(), zoom)
    )

    /** Every tile at [zoom] that overlaps [bounds], as (x, y) pairs. */
    fun tilesCovering(bounds: GeoBounds, zoom: Int): List<TileId> {
        val max = tilesPerAxis(zoom) - 1
        val minX = Math.floor(lngToTileX(bounds.west, zoom)).toInt().coerceIn(0, max)
        // `ceil - 1` rather than `floor` for the far edge: an area ending exactly on a tile
        // boundary stops at the tile before it instead of dragging in a whole extra column that
        // it does not actually overlap.
        val maxX = (Math.ceil(lngToTileX(bounds.east, zoom)).toInt() - 1).coerceIn(minX, max)
        // Tile Y grows southwards, so the northern edge gives the smaller index.
        val minY = Math.floor(latToTileY(bounds.north, zoom)).toInt().coerceIn(0, max)
        val maxY = (Math.ceil(latToTileY(bounds.south, zoom)).toInt() - 1).coerceIn(minY, max)

        val tiles = ArrayList<TileId>((maxX - minX + 1) * (maxY - minY + 1))
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                tiles.add(TileId(zoom, x, y))
            }
        }
        return tiles
    }

    /**
     * Where [lng] falls inside tile [x], in pixels from the tile's left edge. Values outside
     * `0..tileSize` mean the point is in a neighbouring tile, which callers rely on when a cell
     * straddles a tile seam.
     */
    fun pixelXInTile(lng: Double, zoom: Int, x: Int, tileSize: Int = TILE_SIZE_PX): Float =
        ((lngToTileX(lng, zoom) - x) * tileSize).toFloat()

    /** Where [lat] falls inside tile [y], in pixels from the tile's top edge. */
    fun pixelYInTile(lat: Double, zoom: Int, y: Int, tileSize: Int = TILE_SIZE_PX): Float =
        ((latToTileY(lat, zoom) - y) * tileSize).toFloat()

    /**
     * Rough ground width of one tile pixel, in metres, at the tile's own latitude. Used to size the
     * feather blur and to estimate how many grid cells a tile can hold.
     */
    fun metersPerPixel(zoom: Int, latitude: Double, tileSize: Int = TILE_SIZE_PX): Double {
        val equatorMetersPerTile = EARTH_CIRCUMFERENCE_METERS / tilesPerAxis(zoom)
        val latitudeScale = Math.cos(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return equatorMetersPerTile * latitudeScale / tileSize
    }

    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
}

/** Identifies one fog tile. Also the identity half of the tile cache key. */
data class TileId(val zoom: Int, val x: Int, val y: Int) {
    override fun toString(): String = "$zoom/$x/$y"
}
