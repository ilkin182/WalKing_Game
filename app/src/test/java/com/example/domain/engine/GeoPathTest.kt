package com.example.domain.engine

import com.example.domain.model.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoPathTest {
    private val baku = Coordinate(40.4093, 49.8671)

    @Test
    fun `distance matches the known length of a degree of latitude`() {
        val oneDegreeNorth = Coordinate(baku.lat + 1.0, baku.lng)

        assertEquals(111_195.0, GeoPath.distanceMeters(baku, oneDegreeNorth), 100.0)
    }

    @Test
    fun `sampling keeps both endpoints`() {
        val destination = Coordinate(baku.lat + 0.001, baku.lng + 0.001)

        val points = GeoPath.sample(baku, destination, 15.0)

        assertEquals(baku, points.first())
        assertEquals(destination.lat, points.last().lat, 1e-12)
        assertEquals(destination.lng, points.last().lng, 1e-12)
    }

    @Test
    fun `consecutive samples are never further apart than the requested step`() {
        val destination = Coordinate(baku.lat + 0.002, baku.lng + 0.003)

        val points = GeoPath.sample(baku, destination, 15.0)

        assertTrue(points.size > 2)
        points.zipWithNext { a, b -> assertTrue(GeoPath.distanceMeters(a, b) <= 15.0) }
    }

    @Test
    fun `a segment shorter than one step is not subdivided`() {
        val barelyMoved = Coordinate(baku.lat + 0.00001, baku.lng)

        assertEquals(2, GeoPath.sample(baku, barelyMoved, 15.0).size)
    }

    @Test
    fun `a stationary segment collapses onto the same point`() {
        val points = GeoPath.sample(baku, baku, 15.0)

        assertTrue(points.all { it == baku })
    }
}
