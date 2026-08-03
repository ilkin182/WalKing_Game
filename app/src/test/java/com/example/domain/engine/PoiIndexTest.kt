package com.example.domain.engine

import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds
import com.example.domain.model.PoiKind
import com.example.domain.model.PointOfInterest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiIndexTest {

    private fun point(
        id: String,
        kind: PoiKind,
        lat: Double,
        lng: Double,
        name: String? = null
    ) = PointOfInterest(id, kind, name, Coordinate(lat, lng))

    private fun area(
        id: String,
        kind: PoiKind,
        bounds: GeoBounds,
        name: String? = null
    ) = PointOfInterest(id, kind, name, Coordinate(bounds.centerLat, bounds.centerLng), bounds)

    @Test
    fun `an empty index matches nothing`() {
        assertTrue(PoiIndex.EMPTY.matching(40.37, 49.83).isEmpty())
        assertEquals(0, PoiIndex.EMPTY.countOf(PoiKind.PARK))
    }

    @Test
    fun `a position inside a park matches it`() {
        val park = area(
            "way/1",
            PoiKind.PARK,
            GeoBounds(north = 40.380, south = 40.375, east = 49.835, west = 49.830)
        )
        val index = PoiIndex.build(listOf(park))

        assertEquals(listOf(park), index.matching(40.377, 49.832))
    }

    @Test
    fun `a position well outside a park does not`() {
        val park = area(
            "way/1",
            PoiKind.PARK,
            GeoBounds(north = 40.380, south = 40.375, east = 49.835, west = 49.830)
        )
        val index = PoiIndex.build(listOf(park))

        assertTrue(index.matching(40.390, 49.832).isEmpty())
    }

    /**
     * A monument is matched from a little way off, so it must still be found when the tolerance
     * reaches across a tile edge - the case the nine-tile lookup exists for.
     */
    @Test
    fun `a monument just the other side of a tile edge is still found`() {
        // Sits at the very top of its tile; the position is in the tile above.
        val monument = point("node/1", PoiKind.MONUMENT, 40.379_95, 49.832)
        val index = PoiIndex.build(listOf(monument))

        val fromNextTile = index.matching(40.380_2, 49.832)

        assertEquals(listOf(monument), fromNextTile)
        // Same place, comfortably beyond the tolerance: not a match at any distance.
        assertTrue(index.matching(40.385, 49.832).isEmpty())
    }

    @Test
    fun `a park spanning several tiles is found from all of them`() {
        val park = area(
            "way/1",
            PoiKind.PARK,
            GeoBounds(north = 40.40, south = 40.37, east = 49.86, west = 49.83)
        )
        val index = PoiIndex.build(listOf(park))

        assertEquals(listOf(park), index.matching(40.375, 49.835))
        assertEquals(listOf(park), index.matching(40.395, 49.855))
    }

    @Test
    fun `a place reached from several tiles is only counted once`() {
        val park = area(
            "way/1",
            PoiKind.PARK,
            GeoBounds(north = 40.40, south = 40.37, east = 49.86, west = 49.83)
        )
        val index = PoiIndex.build(listOf(park, park))

        assertEquals(1, index.matching(40.385, 49.845).size)
        assertEquals(1, index.countOf(PoiKind.PARK))
    }

    /**
     * A long bridge is mapped as many ways. Counting rows would make "cross five bridges" answerable
     * by walking over one of them, so the denominator counts names.
     */
    @Test
    fun `segments of one named bridge count as one place`() {
        val index = PoiIndex.build(
            listOf(
                point("way/1", PoiKind.BRIDGE, 40.377, 49.832, name = "Bakı körpüsü"),
                point("way/2", PoiKind.BRIDGE, 40.378, 49.833, name = "Bakı körpüsü"),
                point("way/3", PoiKind.BRIDGE, 40.379, 49.834, name = null)
            )
        )

        assertEquals(2, index.countOf(PoiKind.BRIDGE))
    }

    @Test
    fun `counts are kept per kind`() {
        val index = PoiIndex.build(
            listOf(
                point("node/1", PoiKind.MONUMENT, 40.377, 49.832),
                point("node/2", PoiKind.MONUMENT, 40.378, 49.833),
                point("node/3", PoiKind.METRO, 40.379, 49.834)
            )
        )

        assertEquals(2, index.countOf(PoiKind.MONUMENT))
        assertEquals(1, index.countOf(PoiKind.METRO))
        assertEquals(0, index.countOf(PoiKind.COAST))
        assertEquals(3, index.all.size)
    }
}
