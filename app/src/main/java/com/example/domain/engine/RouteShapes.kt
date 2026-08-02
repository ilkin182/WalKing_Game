package com.example.domain.engine

import com.example.domain.model.RoutePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What one walk's path turned out to look like. */
data class RouteShape(
    /** The walk ended back where it set out from, having actually gone somewhere first. */
    val isBoomerang: Boolean = false,
    /** The longest stretch walked in essentially one direction, in metres. */
    val longestStraightMeters: Double = 0.0,
    /** How many times the walk turned sharply. */
    val turns: Int = 0,
    /** The walk closed a four-cornered, right-angled, roughly equal-sided loop. */
    val isSquare: Boolean = false
) {
    companion object {
        val NONE = RouteShape()
    }
}

/**
 * Reads the shape out of the line a walk traced.
 *
 * Everything here works on a flat local projection in metres rather than on degrees: a degree of
 * longitude is only two thirds of a degree of latitude in Baku, and angles measured on raw lat/lng
 * would be skewed by exactly that much - which matters enormously when the question is whether a
 * corner was a right angle.
 *
 * Projecting a walk onto a plane is safe because a walk is a few kilometres across; the error against
 * the true curved surface stays well under a metre, far below the GPS noise the thresholds already
 * have to absorb.
 *
 * Pure, so every rule below is exercised in unit tests from a hand-drawn list of points.
 */
object RouteShapes {

    // -------------------------------------------------------------------- Bumeranq

    /** How close to the starting point the walk has to finish to count as having come back. */
    private const val RETURN_RADIUS_METERS = 60.0

    /** How far away it has to have got first, so pacing around the front door is not a boomerang. */
    private const val MIN_EXCURSION_METERS = 300.0

    // -------------------------------------------------------------------- Düz Xətt

    /** How far the path may stray from the straight line between the ends of a stretch. */
    private const val MAX_STRAIGHT_DEVIATION_METERS = 75.0

    // -------------------------------------------------------------------- Zigzaq

    /** A change of heading beyond this is a turn rather than a bend in the road. */
    private const val TURN_DEGREES = 45.0

    /** Segments shorter than this are GPS jitter, and their heading means nothing. */
    private const val MIN_TURN_SEGMENT_METERS = 15.0

    // -------------------------------------------------------------------- Həndəsəçi

    /** Smallest loop that counts as a drawn square rather than a lap of a car park. */
    private const val MIN_SQUARE_PERIMETER_METERS = 400.0

    /** How far a corner may be off ninety degrees. */
    private const val SQUARE_ANGLE_TOLERANCE_DEGREES = 25.0

    /** The longest side may be at most this many times the shortest. */
    private const val MAX_SQUARE_SIDE_RATIO = 1.4

    /** A vertex that bends less than this is a wobble along a side, not a corner of the shape. */
    private const val CORNER_DEGREES = 35.0

    /** How much of the loop's own size the simplification is allowed to smooth away. */
    private const val SIMPLIFY_FRACTION_OF_PERIMETER = 0.02
    private const val MIN_SIMPLIFY_EPSILON_METERS = 12.0

    private const val METERS_PER_DEGREE = 111_111.0

    fun analyze(points: List<RoutePoint>): RouteShape {
        if (points.size < 3) return RouteShape.NONE
        val path = project(points)

        return RouteShape(
            isBoomerang = isBoomerang(path),
            longestStraightMeters = longestStraightRun(path),
            turns = countTurns(path),
            isSquare = isSquare(path)
        )
    }

    /**
     * The walk finished where it started, having been somewhere in between.
     *
     * Both halves are needed: without the excursion, a player who never left their street corner
     * would have walked a "loop" every time they switched tracking on and off.
     */
    private fun isBoomerang(path: List<Point>): Boolean {
        val start = path.first()
        val farthest = path.maxOf { it.distanceTo(start) }
        return farthest >= MIN_EXCURSION_METERS &&
            path.last().distanceTo(start) <= RETURN_RADIUS_METERS
    }

    /**
     * The longest stretch the player held one direction for, measured end to end.
     *
     * "Straight" is judged by how far the path strays from the line joining the ends of the stretch,
     * not by heading: a road that bends by two degrees every hundred metres never trips a heading
     * test but is unmistakably not a straight three kilometres.
     *
     * The path is cut greedily - a stretch is extended until it can no longer stay within tolerance,
     * and the next one starts where it broke - so the walk is read the way it was walked rather than
     * every stretch in it being tried against every other.
     */
    private fun longestStraightRun(path: List<Point>): Double {
        var longest = 0.0
        var anchor = 0

        for (end in 1 until path.size) {
            if (withinCorridor(path, anchor, end)) continue

            // This point broke the stretch, so the previous one ended it. The next stretch starts
            // there rather than here, otherwise a stretch could never begin mid-corner.
            longest = max(longest, path[anchor].distanceTo(path[end - 1]))
            anchor = end - 1
        }
        return max(longest, path[anchor].distanceTo(path.last()))
    }

    /** Whether every point between [from] and [to] lies close to the straight line joining them. */
    private fun withinCorridor(path: List<Point>, from: Int, to: Int): Boolean {
        val start = path[from]
        val end = path[to]
        for (i in from + 1 until to) {
            if (path[i].distanceToLine(start, end) > MAX_STRAIGHT_DEVIATION_METERS) return false
        }
        return true
    }

    /**
     * How many sharp turns the walk made.
     *
     * Measured between segments that are long enough to have a meaningful direction: two fixes a
     * couple of metres apart point wherever the noise happened to push them, and counting those
     * would hand the zigzag achievement to anyone who stood still for long enough.
     */
    private fun countTurns(path: List<Point>): Int {
        var turns = 0
        var previousHeading: Double? = null
        var from = path.first()

        for (index in 1 until path.size) {
            val to = path[index]
            if (from.distanceTo(to) < MIN_TURN_SEGMENT_METERS) continue

            val heading = from.headingTo(to)
            previousHeading?.let { if (headingChange(it, heading) > TURN_DEGREES) turns++ }
            previousHeading = heading
            from = to
        }
        return turns
    }

    /**
     * Whether the walk drew a square.
     *
     * The path is first simplified, which is what turns a few hundred noisy fixes into the handful
     * of corners a person would say they walked; the tolerance scales with the size of the loop, so
     * a small square is not smoothed out of existence and a large one is not read as having a corner
     * everywhere the pavement wobbled. What survives has to be four corners, each near a right
     * angle, with sides of roughly one length.
     */
    private fun isSquare(path: List<Point>): Boolean {
        val perimeter = path.zipWithNext().sumOf { (from, to) -> from.distanceTo(to) }
        if (perimeter < MIN_SQUARE_PERIMETER_METERS) return false
        if (path.last().distanceTo(path.first()) > RETURN_RADIUS_METERS) return false

        val epsilon = max(
            MIN_SIMPLIFY_EPSILON_METERS,
            perimeter * SIMPLIFY_FRACTION_OF_PERIMETER
        )
        // The last point is the closing one; keeping it would leave the ring with the starting
        // corner counted twice.
        val ring = simplify(path, epsilon).dropLast(1)
        if (ring.size < 4) return false

        // Where the player happened to start is rarely a corner of the shape they walked, and the
        // simplification always keeps it. Dropping the vertices that barely bend removes it, and
        // any other wobble the tolerance left behind.
        val corners = ring.indices
            .filter { cornerAngle(ring, it) <= 180.0 - CORNER_DEGREES }
            .map { ring[it] }
        if (corners.size != 4) return false

        val angles = corners.indices.map { cornerAngle(corners, it) }
        if (angles.any { abs(it - 90.0) > SQUARE_ANGLE_TOLERANCE_DEGREES }) return false

        val sides = corners.indices.map { corners[it].distanceTo(corners[(it + 1) % corners.size]) }
        val shortest = sides.min()
        return shortest > 0.0 && sides.max() / shortest <= MAX_SQUARE_SIDE_RATIO
    }

    /** The angle the ring turns through at vertex [index], in degrees, where 180 is dead straight. */
    private fun cornerAngle(ring: List<Point>, index: Int): Double {
        val previous = ring[(index - 1 + ring.size) % ring.size]
        val vertex = ring[index]
        val next = ring[(index + 1) % ring.size]

        val incoming = vertex.headingTo(previous)
        val outgoing = vertex.headingTo(next)
        return headingChange(incoming, outgoing)
    }

    /**
     * Ramer-Douglas-Peucker: the fewest points that still describe the path to within [epsilon].
     *
     * Iterative rather than recursive - a walk can be thousands of points long, and the recursion
     * depth of the textbook version is not bounded by anything the app controls.
     */
    private fun simplify(path: List<Point>, epsilon: Double): List<Point> {
        if (path.size < 3) return path

        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.lastIndex] = true

        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.addLast(0 to path.lastIndex)

        while (pending.isNotEmpty()) {
            val (from, to) = pending.removeLast()
            if (to <= from + 1) continue

            var farthest = -1
            var farthestDistance = 0.0
            for (i in from + 1 until to) {
                val distance = path[i].distanceToLine(path[from], path[to])
                if (distance > farthestDistance) {
                    farthestDistance = distance
                    farthest = i
                }
            }

            if (farthestDistance > epsilon && farthest > 0) {
                keep[farthest] = true
                pending.addLast(from to farthest)
                pending.addLast(farthest to to)
            }
        }
        return path.filterIndexed { index, _ -> keep[index] }
    }

    /**
     * The walk on a flat plane in metres, with its first point as the origin.
     *
     * Longitude is scaled by the cosine of the latitude, without which every angle in a walk would
     * be measured on a stretched map and no corner would ever come out square.
     */
    private fun project(points: List<RoutePoint>): List<Point> {
        val origin = points.first()
        val lngScale = cos(Math.toRadians(origin.lat)) * METERS_PER_DEGREE
        return points.map { point ->
            Point(
                x = (point.lng - origin.lng) * lngScale,
                y = (point.lat - origin.lat) * METERS_PER_DEGREE
            )
        }
    }

    /** The smaller of the two ways round between two headings, so 350 to 10 is 20 and not 340. */
    private fun headingChange(from: Double, to: Double): Double {
        val difference = abs(to - from) % 360.0
        return min(difference, 360.0 - difference)
    }

    /** A position on the walk, in metres from where it started. */
    private data class Point(val x: Double, val y: Double) {

        fun distanceTo(other: Point): Double = hypot(other.x - x, other.y - y)

        fun headingTo(other: Point): Double =
            (Math.toDegrees(atan2(other.y - y, other.x - x)) + 360.0) % 360.0

        /**
         * How far this point is from the line through [start] and [end].
         *
         * When the two are the same point there is no line to speak of, so the distance to that
         * point is the honest answer - which is what keeps a stationary stretch from reading as a
         * perfectly straight one.
         */
        fun distanceToLine(start: Point, end: Point): Double {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val length = hypot(dx, dy)
            if (length == 0.0) return distanceTo(start)
            return abs(dy * (x - start.x) - dx * (y - start.y)) / length
        }
    }
}
