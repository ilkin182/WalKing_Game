package com.example.domain.engine

import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds

/**
 * The fixed ~1 km grid the points of interest are fetched and cached in.
 *
 * Overpass is a free, shared, donated service. Asking it about the ground under each cell as the
 * player walks would be a request every few seconds and would get the app blocked, deservedly - so
 * nothing is ever queried per cell. The world is cut into fixed squares instead, each square is
 * asked about at most once, and the answer is kept in Room. A tile is a *fixed* square rather than a
 * box around the player, which is the point: two players, or the same player on two different walks,
 * ask for exactly the same squares, so a tile already in the cache is recognised as such rather than
 * being re-fetched because the box was drawn from a slightly different centre.
 *
 * The grid is in degrees, not metres, so its squares are not quite square: [TILE_DEGREES] is about
 * 1.1 km north to south everywhere, and about 850 m east to west at Baku's latitude, narrowing
 * further towards the poles. That is fine - the tile is a fetch unit, not a measurement - and it is
 * what makes a key derivable from a coordinate with two divisions and no projection.
 */
object PoiTiles {

    /** ~1.1 km of latitude. See the note above on what this is east to west. */
    const val TILE_DEGREES = 0.01

    /** The tile containing a position. */
    fun keyOf(lat: Double, lng: Double): String =
        "${indexOf(lat)}:${indexOf(lng)}"

    fun keyOf(point: Coordinate): String = keyOf(point.lat, point.lng)

    /** The area a key covers, or null when the key was not produced by [keyOf]. */
    fun boundsOf(key: String): GeoBounds? {
        val parts = key.split(':')
        if (parts.size != 2) return null
        val latIndex = parts[0].toLongOrNull() ?: return null
        val lngIndex = parts[1].toLongOrNull() ?: return null
        val south = latIndex * TILE_DEGREES
        val west = lngIndex * TILE_DEGREES
        return GeoBounds(
            north = south + TILE_DEGREES,
            south = south,
            east = west + TILE_DEGREES,
            west = west
        )
    }

    /**
     * The tile containing a position plus the eight around it.
     *
     * Places are filed under every tile their outline touches, but a place is also *reachable* from
     * a little outside it - [com.example.domain.model.PoiKind.reachMeters] - and a tolerance of up
     * to 150 m can cross a tile edge. Looking at the ring as well costs nine map lookups and removes
     * the whole class of bug where a monument stops counting because it sits just the wrong side of
     * an invisible line.
     */
    fun keysAround(lat: Double, lng: Double): List<String> {
        val latIndex = indexOf(lat)
        val lngIndex = indexOf(lng)
        return buildList(9) {
            for (dLat in -1..1) {
                for (dLng in -1..1) add("${latIndex + dLat}:${lngIndex + dLng}")
            }
        }
    }

    /** Every tile a box overlaps, for filing a place that spans more than one. */
    fun keysCovering(bounds: GeoBounds): List<String> {
        val south = indexOf(bounds.south)
        val north = indexOf(bounds.north)
        val west = indexOf(bounds.west)
        val east = indexOf(bounds.east)
        return buildList {
            for (latIndex in south..north) {
                for (lngIndex in west..east) add("$latIndex:$lngIndex")
            }
        }
    }

    private fun indexOf(degrees: Double): Long = Math.floorDiv(
        Math.round(degrees * SCALE),
        Math.round(TILE_DEGREES * SCALE)
    )

    /**
     * Degrees are scaled to whole numbers before the division so a coordinate exactly on a tile edge
     * lands on one side of it every time. Going through `floor(lat / 0.01)` directly, 40.37 comes out
     * as 4036 about as often as 4037 depending on which arithmetic produced the 40.37, and a cell
     * would then flip between two tiles from one run to the next.
     */
    private const val SCALE = 1_000_000.0
}
