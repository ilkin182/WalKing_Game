package com.example.domain.engine

import com.example.domain.model.Coordinate

/**
 * Single source of truth for the grid's cell size and for how far the grid extends. Every use case
 * that stomps or renders cells must resolve lat/lng through this same resolution, otherwise stomped
 * addresses and rendered cell addresses would be computed against different grids and silently stop
 * matching each other.
 */
object HexGridConfig {
    const val RESOLUTION = 11

    /**
     * How much ground one cell covers, in square metres.
     *
     * The area every "explored territory" figure in the app is derived from, so the profile's total
     * and its day-by-day breakdown can never disagree about how big a cell is. Cells at
     * [RESOLUTION] are not all exactly this size - a hexagonal grid on a sphere cannot be - so this
     * is the nominal figure for the resolution rather than a per-cell measurement.
     */
    const val CELL_AREA_SQUARE_METERS = 2100.0

    /**
     * The grid is defined over a 100 km radius around the player, so the whole area they can
     * realistically reach in a session is divided into cells - not just the small patch that
     * happens to be on screen. Anything further away is outside the playable region and is not
     * tiled at all.
     */
    const val COVERAGE_RADIUS_METERS = 100_000.0

    /**
     * How finely the segment between two GPS fixes is sampled when claiming the trail the player
     * walked along it. A cell at [RESOLUTION] measures about 21 m from its center to an edge, so
     * two points this far apart always land in the same cell or in directly neighbouring ones -
     * which is what guarantees the claimed trail comes out as one unbroken chain with no holes,
     * however fast the player is moving.
     */
    const val PATH_SAMPLE_STEP_METERS = 15.0

    private const val METERS_PER_DEGREE_LAT = 111_111.0

    // Web Mercator (the map projection) is undefined at the poles; clamp there instead of
    // producing latitudes the renderer cannot project.
    private const val MAX_MERCATOR_LAT = 85.0

    // Guards against the 1/cos(lat) blow-up in the longitude conversion near the poles.
    private const val MIN_COS_LAT = 0.01

    /**
     * The axis-aligned box covering [COVERAGE_RADIUS_METERS] around the given point, returned as
     * NW, NE, SE, SW corners.
     */
    fun coverageBounds(centerLat: Double, centerLng: Double): List<Coordinate> {
        val clampedLat = centerLat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val deltaLat = COVERAGE_RADIUS_METERS / METERS_PER_DEGREE_LAT
        val cosLat = Math.cos(Math.toRadians(clampedLat)).coerceAtLeast(MIN_COS_LAT)
        val deltaLng = (COVERAGE_RADIUS_METERS / (METERS_PER_DEGREE_LAT * cosLat)).coerceAtMost(180.0)

        val north = (clampedLat + deltaLat).coerceAtMost(MAX_MERCATOR_LAT)
        val south = (clampedLat - deltaLat).coerceAtLeast(-MAX_MERCATOR_LAT)
        val east = (centerLng + deltaLng).coerceAtMost(180.0)
        val west = (centerLng - deltaLng).coerceAtLeast(-180.0)

        return listOf(
            Coordinate(north, west),
            Coordinate(north, east),
            Coordinate(south, east),
            Coordinate(south, west)
        )
    }

    /**
     * Intersects a requested area (typically the map's visible bounding box) with the 100 km
     * coverage area around [centerLat]/[centerLng]. Returns null when the requested area lies
     * entirely outside the coverage area, so callers can skip tiling it altogether.
     *
     * Both areas are treated as their bounding boxes: the caller passes a viewport rectangle, and
     * an axis-aligned intersection is all the grid scan needs.
     */
    fun clipToCoverage(bounds: List<Coordinate>, centerLat: Double, centerLng: Double): List<Coordinate>? {
        if (bounds.isEmpty()) return null
        val coverage = coverageBounds(centerLat, centerLng)

        val north = minOf(bounds.maxOf { it.lat }, coverage.maxOf { it.lat })
        val south = maxOf(bounds.minOf { it.lat }, coverage.minOf { it.lat })
        val east = minOf(bounds.maxOf { it.lng }, coverage.maxOf { it.lng })
        val west = maxOf(bounds.minOf { it.lng }, coverage.minOf { it.lng })

        if (north <= south || east <= west) return null

        return listOf(
            Coordinate(north, west),
            Coordinate(north, east),
            Coordinate(south, east),
            Coordinate(south, west)
        )
    }
}
