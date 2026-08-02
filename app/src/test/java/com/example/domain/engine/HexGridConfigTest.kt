package com.example.domain.engine

import com.example.domain.model.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexGridConfigTest {
    private val bakuLat = 40.4093
    private val bakuLng = 49.8671

    @Test
    fun `coverage bounds span 100 km in every direction`() {
        val bounds = HexGridConfig.coverageBounds(bakuLat, bakuLng)

        val northSouthMeters = (bounds.maxOf { it.lat } - bounds.minOf { it.lat }) * 111_111.0
        val eastWestMeters = (bounds.maxOf { it.lng } - bounds.minOf { it.lng }) *
            111_111.0 * Math.cos(Math.toRadians(bakuLat))

        assertEquals(200_000.0, northSouthMeters, 1_000.0)
        assertEquals(200_000.0, eastWestMeters, 1_000.0)
    }

    @Test
    fun `coverage bounds stay projectable near the poles`() {
        val bounds = HexGridConfig.coverageBounds(89.5, 10.0)

        assertTrue(bounds.all { it.lat <= 85.0 && it.lat >= -85.0 })
        assertTrue(bounds.all { it.lng in -180.0..180.0 })
    }

    @Test
    fun `clipping keeps an area that is fully covered untouched`() {
        val viewport = listOf(
            Coordinate(bakuLat + 0.01, bakuLng - 0.01),
            Coordinate(bakuLat + 0.01, bakuLng + 0.01),
            Coordinate(bakuLat - 0.01, bakuLng + 0.01),
            Coordinate(bakuLat - 0.01, bakuLng - 0.01)
        )

        val clipped = HexGridConfig.clipToCoverage(viewport, bakuLat, bakuLng)

        assertNotNull(clipped)
        assertEquals(viewport.maxOf { it.lat }, clipped!!.maxOf { it.lat }, 1e-9)
        assertEquals(viewport.minOf { it.lng }, clipped.minOf { it.lng }, 1e-9)
    }

    @Test
    fun `clipping returns null for an area outside the coverage radius`() {
        val paris = listOf(
            Coordinate(48.87, 2.34),
            Coordinate(48.87, 2.36),
            Coordinate(48.85, 2.36),
            Coordinate(48.85, 2.34)
        )

        assertNull(HexGridConfig.clipToCoverage(paris, bakuLat, bakuLng))
    }

    @Test
    fun `clipping an empty area returns null`() {
        assertNull(HexGridConfig.clipToCoverage(emptyList(), bakuLat, bakuLng))
    }
}
