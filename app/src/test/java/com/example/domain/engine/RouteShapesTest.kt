package com.example.domain.engine

import com.example.domain.model.RoutePoint
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routes are drawn in metres and converted to lat/lng on the way in, so each test reads as the shape
 * it is testing rather than as a column of coordinates.
 */
class RouteShapesTest {

    @Test
    fun `a route too short to have a shape yields nothing`() {
        assertEquals(RouteShape.NONE, RouteShapes.analyze(emptyList()))
        assertEquals(RouteShape.NONE, RouteShapes.analyze(walk(0.0 to 0.0, 100.0 to 0.0)))
    }

    // ------------------------------------------------------------------------ Bumeranq

    @Test
    fun `walking out and back is a boomerang`() {
        val there = (0..10).map { it * 50.0 to 0.0 }
        val back = (10 downTo 0).map { it * 50.0 to 0.0 }

        assertTrue(RouteShapes.analyze(walk(there + back)).isBoomerang)
    }

    @Test
    fun `pacing about the front door is not a boomerang`() {
        // Ends where it started, but never went anywhere - which is what tracking switched on and
        // off in one spot looks like.
        val there = (0..5).map { it * 20.0 to 0.0 }
        val back = (5 downTo 0).map { it * 20.0 to 0.0 }

        assertFalse(RouteShapes.analyze(walk(there + back)).isBoomerang)
    }

    @Test
    fun `a walk that ends somewhere else is not a boomerang`() {
        assertFalse(RouteShapes.analyze(walk((0..30).map { it * 100.0 to 0.0 })).isBoomerang)
    }

    // ------------------------------------------------------------------------ Düz Xətt

    @Test
    fun `three kilometres in one direction is measured end to end`() {
        val route = walk((0..30).map { it * 100.0 to 0.0 })

        assertEquals(3_000.0, RouteShapes.analyze(route).longestStraightMeters, 1.0)
    }

    @Test
    fun `a road that wanders slightly still counts as straight`() {
        // Ten metres off the line every so often is a pavement, not a change of direction.
        val route = walk((0..30).map { it * 100.0 to if (it % 2 == 0) 10.0 else -10.0 })

        assertTrue(RouteShapes.analyze(route).longestStraightMeters > 2_900.0)
    }

    @Test
    fun `a right-angled turn ends the straight stretch`() {
        val east = (0..20).map { it * 100.0 to 0.0 }
        val north = (1..19).map { 2_000.0 to it * 100.0 }
        val longest = RouteShapes.analyze(walk(east + north)).longestStraightMeters

        // Each arm is two kilometres; the four kilometres walked in total are not a straight line.
        assertTrue("was $longest", longest in 1_900.0..2_200.0)
    }

    // ------------------------------------------------------------------------ Zigzaq

    @Test
    fun `alternating right angles are counted as turns`() {
        // Twenty-two segments of thirty metres, each at right angles to the one before.
        val points = mutableListOf(0.0 to 0.0)
        repeat(11) { step ->
            points += (step * 30.0 + 30.0) to (step * 30.0)
            points += (step * 30.0 + 30.0) to (step * 30.0 + 30.0)
        }

        assertEquals(21, RouteShapes.analyze(walk(points)).turns)
    }

    @Test
    fun `a straight walk has no turns`() {
        assertEquals(0, RouteShapes.analyze(walk((0..30).map { it * 100.0 to 0.0 })).turns)
    }

    @Test
    fun `jitter while standing still is not a turn`() {
        // Fixes a couple of metres apart point wherever the noise pushed them; counting those would
        // hand the achievement to anyone who stopped for a coffee.
        val points = (0..40).map { step ->
            (if (step % 2 == 0) 0.0 else 2.0) to (if (step % 4 < 2) 0.0 else 2.0)
        }

        assertEquals(0, RouteShapes.analyze(walk(points)).turns)
    }

    // ------------------------------------------------------------------------ Həndəsəçi

    @Test
    fun `four sides and four right angles is a square`() {
        assertTrue(RouteShapes.analyze(walk(square(side = 200.0))).isSquare)
    }

    @Test
    fun `a square is recognised even when the walk began mid-side`() {
        // Where the player switched tracking on is almost never a corner, and the simplification
        // always keeps that first point - so it has to be dropped for being straight, not kept as
        // a fifth corner.
        val ring = square(side = 200.0).dropLast(1)
        val startIndex = ring.size / 8
        val rotated = ring.drop(startIndex) + ring.take(startIndex + 1)

        assertTrue(RouteShapes.analyze(walk(rotated)).isSquare)
    }

    @Test
    fun `a rectangle is not a square`() {
        assertFalse(RouteShapes.analyze(walk(rectangle(width = 400.0, height = 150.0))).isSquare)
    }

    @Test
    fun `a circle is not a square`() {
        val circle = (0..128).map { step ->
            val angle = Math.toRadians(step * 360.0 / 128)
            150.0 * cos(angle) to 150.0 * Math.sin(angle)
        }

        assertFalse(RouteShapes.analyze(walk(circle)).isSquare)
    }

    @Test
    fun `a square that never closed is not a square`() {
        assertFalse(RouteShapes.analyze(walk(square(side = 200.0).dropLast(4))).isSquare)
    }

    @Test
    fun `a lap of a car park is too small to count`() {
        assertFalse(RouteShapes.analyze(walk(square(side = 60.0))).isSquare)
    }

    // ------------------------------------------------------------------------ Helpers

    /** A closed square walked anticlockwise from one corner, sampled every twenty metres. */
    private fun square(side: Double): List<Pair<Double, Double>> = rectangle(side, side)

    private fun rectangle(width: Double, height: Double): List<Pair<Double, Double>> {
        val corners = listOf(
            0.0 to 0.0,
            width to 0.0,
            width to height,
            0.0 to height,
            0.0 to 0.0
        )
        return corners.zipWithNext().flatMap { (from, to) -> sample(from, to) } + corners.last()
    }

    /** Points along one side, excluding its end, so consecutive sides do not repeat a corner. */
    private fun sample(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        stepMeters: Double = 20.0
    ): List<Pair<Double, Double>> {
        val length = Math.hypot(to.first - from.first, to.second - from.second)
        val steps = Math.ceil(length / stepMeters).toInt().coerceAtLeast(1)
        return (0 until steps).map { step ->
            val fraction = step.toDouble() / steps
            (from.first + (to.first - from.first) * fraction) to
                (from.second + (to.second - from.second) * fraction)
        }
    }

    private fun walk(vararg eastNorth: Pair<Double, Double>): List<RoutePoint> =
        walk(eastNorth.toList())

    /** Metres east and north of a point in Baku, as the route points the analyser reads. */
    private fun walk(eastNorth: List<Pair<Double, Double>>): List<RoutePoint> =
        eastNorth.mapIndexed { index, (east, north) ->
            RoutePoint(
                lat = ORIGIN_LAT + north / METERS_PER_DEGREE,
                lng = ORIGIN_LNG + east / (cos(Math.toRadians(ORIGIN_LAT)) * METERS_PER_DEGREE),
                at = index * 10_000L
            )
        }

    private companion object {
        const val ORIGIN_LAT = 40.4093
        const val ORIGIN_LNG = 49.8671
        const val METERS_PER_DEGREE = 111_111.0
    }
}
