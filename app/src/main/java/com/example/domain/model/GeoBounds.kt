package com.example.domain.model

/**
 * An axis-aligned lat/lng rectangle.
 *
 * The grid only ever covers a 100 km radius around the player ([com.example.domain.engine.HexGridConfig]),
 * so a box can never wrap the antimeridian in practice and the comparisons here treat longitude as
 * a plain interval.
 */
data class GeoBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    val centerLat: Double get() = (north + south) / 2.0
    val centerLng: Double get() = (east + west) / 2.0

    fun intersects(other: GeoBounds): Boolean =
        south <= other.north && north >= other.south && west <= other.east && east >= other.west

    fun contains(lat: Double, lng: Double): Boolean =
        lat in south..north && lng in west..east

    /** Grows the box by [degreesLat]/[degreesLng] on every side. */
    fun expand(degreesLat: Double, degreesLng: Double): GeoBounds = GeoBounds(
        north = north + degreesLat,
        south = south - degreesLat,
        east = east + degreesLng,
        west = west - degreesLng
    )

    fun toCorners(): List<Coordinate> = listOf(
        Coordinate(north, west),
        Coordinate(north, east),
        Coordinate(south, east),
        Coordinate(south, west)
    )

    companion object {
        /** The bounding box of a set of points, or null when [points] is empty. */
        fun of(points: List<Coordinate>): GeoBounds? {
            if (points.isEmpty()) return null
            return GeoBounds(
                north = points.maxOf { it.lat },
                south = points.minOf { it.lat },
                east = points.maxOf { it.lng },
                west = points.minOf { it.lng }
            )
        }
    }
}
