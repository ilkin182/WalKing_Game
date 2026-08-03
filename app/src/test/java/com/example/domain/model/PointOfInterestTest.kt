package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointOfInterestTest {

    private fun spot(kind: PoiKind, name: String? = null) =
        PointOfInterest("node/1", kind, name, Coordinate(40.3770, 49.8320))

    @Test
    fun `a place reaches its own position`() {
        assertTrue(spot(PoiKind.MONUMENT).reaches(40.3770, 49.8320))
    }

    @Test
    fun `the tolerance differs by kind`() {
        // ~50 m north of the place: within a monument's reach, outside a bridge's.
        val north = 40.3770 + 50 / 111_111.0

        assertTrue(spot(PoiKind.MONUMENT).reaches(north, 49.8320))
        assertFalse(spot(PoiKind.BRIDGE).reaches(north, 49.8320))
    }

    @Test
    fun `nothing is reached from a kilometre away`() {
        PoiKind.entries.forEach { kind ->
            assertFalse(kind.name, spot(kind).reaches(40.3860, 49.8320))
        }
    }

    @Test
    fun `an area is reached anywhere inside it, not only at its centre`() {
        val park = PointOfInterest(
            id = "way/1",
            kind = PoiKind.PARK,
            name = "Dənizkənarı",
            center = Coordinate(40.3770, 49.8320),
            bounds = GeoBounds(north = 40.3800, south = 40.3740, east = 49.8360, west = 49.8280)
        )

        assertTrue(park.reaches(40.3795, 49.8355))
        assertTrue(park.reaches(40.3745, 49.8285))
        assertFalse(park.reaches(40.3850, 49.8320))
    }

    @Test
    fun `the tolerance stays symmetric in metres as longitude narrows`() {
        // Same place at a high latitude, where a degree of longitude is much shorter than one of
        // latitude. An east-west tolerance in raw degrees would shrink to nothing here.
        val arctic = PointOfInterest(
            "node/1", PoiKind.METRO, null, Coordinate(78.2200, 15.6500)
        )

        // ~100 m east, well inside a metro station's reach.
        val eastwards = 15.6500 + 100 / (111_111.0 * Math.cos(Math.toRadians(78.22)))

        assertTrue(arctic.reaches(78.2200, eastwards))
    }

    // ---------------------------------------------------------------- identity

    @Test
    fun `a named place is identified by its name so its segments merge`() {
        val a = PointOfInterest("way/1", PoiKind.BRIDGE, "Böyük körpü", Coordinate(40.377, 49.832))
        val b = PointOfInterest("way/2", PoiKind.BRIDGE, "Böyük körpü", Coordinate(40.378, 49.833))

        assertEquals(a.identity, b.identity)
    }

    @Test
    fun `two kinds sharing a name are still two places`() {
        val park = PointOfInterest("way/1", PoiKind.PARK, "Nizami", Coordinate(40.377, 49.832))
        val metro = PointOfInterest("node/2", PoiKind.METRO, "Nizami", Coordinate(40.377, 49.832))

        assertTrue(park.identity != metro.identity)
    }

    @Test
    fun `an unnamed place is its own place`() {
        val a = PointOfInterest("way/1", PoiKind.BRIDGE, null, Coordinate(40.377, 49.832))
        val b = PointOfInterest("way/2", PoiKind.BRIDGE, "  ", Coordinate(40.378, 49.833))

        assertEquals("way/1", a.identity)
        assertEquals("way/2", b.identity)
    }

    @Test
    fun `a town with no bounds is not a found one`() {
        assertFalse(CityBounds("Bakı", "AZ", bounds = null, center = null).found)
        assertFalse(
            CityBounds("Bakı", "AZ", bounds = null, center = Coordinate(40.4, 49.8)).found
        )
        assertTrue(
            CityBounds(
                city = "Bakı",
                countryCode = "AZ",
                bounds = GeoBounds(40.5, 40.3, 50.0, 49.7),
                center = Coordinate(40.4, 49.8)
            ).found
        )
    }
}
