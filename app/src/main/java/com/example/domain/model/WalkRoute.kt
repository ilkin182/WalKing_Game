package com.example.domain.model

/**
 * The line a single walk traced on the ground.
 *
 * Kept apart from the claimed cells because the cells are a set and a route is a sequence: the cells
 * can say *where* the player has been but never in what order, and every question about the shape of
 * a walk - did it come back to where it started, how many times did it turn - is a question about
 * the order.
 *
 * One route belongs to one [WalkSession], so two loops of the same park on different evenings are
 * two routes rather than one confused figure of eight.
 */
data class WalkRoute(
    val sessionId: Long,
    /** In the order they were walked, oldest first. */
    val points: List<RoutePoint>
)

/** One position on a walk. */
data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val at: Long
) {
    val coordinate: Coordinate get() = Coordinate(lat, lng)
}
