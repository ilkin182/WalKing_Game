package com.example.domain.engine

import com.example.domain.model.Coordinate

/**
 * Geodesic helpers for the straight segment a player covers between two GPS fixes.
 *
 * GPS only reports a position every few seconds, so a moving player lands tens of metres away from
 * the previous fix. Resolving only the fixes themselves into cells therefore leaves unclaimed holes
 * along the trail whenever a single step of movement is longer than one cell; densifying the
 * segment in between is what keeps the stomped trail continuous.
 */
object GeoPath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(from: Coordinate, to: Coordinate): Double {
        val dLat = Math.toRadians(to.lat - from.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(from.lat)) * Math.cos(Math.toRadians(to.lat)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /**
     * Points along [from] -> [to] spaced at most [stepMeters] apart, both endpoints included.
     *
     * Over the few hundred metres a single segment can span, the difference between a great circle
     * and a straight line in lat/lng is far below GPS accuracy, so the points are interpolated
     * linearly.
     */
    fun sample(from: Coordinate, to: Coordinate, stepMeters: Double): List<Coordinate> {
        require(stepMeters > 0) { "stepMeters must be positive" }

        val steps = Math.ceil(distanceMeters(from, to) / stepMeters).toInt().coerceAtLeast(1)
        return (0..steps).map { step ->
            val fraction = step.toDouble() / steps
            Coordinate(
                lat = from.lat + (to.lat - from.lat) * fraction,
                lng = from.lng + (to.lng - from.lng) * fraction
            )
        }
    }
}
