package com.example.domain.achievement

import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.ExploredCell
import com.example.domain.model.GeoBounds
import com.example.domain.model.PoiKind
import com.example.domain.model.PointOfInterest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The statistics that come from what was already on the ground - the parks, monuments, bridges and
 * coastline fetched from OpenStreetMap, and the extent of the town itself.
 *
 * Two themes run through these. First, knowing nothing must read as *not yet measured* rather than
 * as a score of zero out of nothing: the background pass that fills the cache runs for hours behind
 * a new install, and a coverage percentage over an empty set would be either 0% or 100% and both
 * would be untrue. Second, the counts are of places rather than of encounters - walking the same
 * park every morning for a year is one park.
 */
class PlayerStatsGeographyTest {

    /**
     * Cells are named for where they are: `"40.3770,49.8320"` resolves to exactly that point. Real
     * cell ids are opaque grid addresses and the calculator only ever resolves them through the
     * lambda it is given, so this keeps a test's geography readable at its call site.
     */
    private fun centerOf(cellId: String): Coordinate? {
        val parts = cellId.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lng = parts[1].toDoubleOrNull() ?: return null
        return Coordinate(lat, lng)
    }

    private fun cellsAt(vararg points: Pair<Double, Double>): List<ExploredCell> =
        points.mapIndexed { i, (lat, lng) ->
            ExploredCell("$lat,$lng", 1_000L + i, ExploredCell.LEVEL_WALKED)
        }

    private fun calculate(
        cells: List<ExploredCell>,
        pois: List<PointOfInterest> = emptyList(),
        cityBounds: List<CityBounds> = emptyList()
    ) = PlayerStatsCalculator.calculate(
        cells = cells,
        totalDistanceMeters = 0.0,
        regionStats = emptyList(),
        statsStartMillis = 0L,
        pois = pois,
        cityBounds = cityBounds,
        resolveCenter = ::centerOf
    )

    private fun park(id: String, name: String?, north: Double, south: Double, east: Double, west: Double) =
        PointOfInterest(
            id = id,
            kind = PoiKind.PARK,
            name = name,
            center = Coordinate((north + south) / 2, (east + west) / 2),
            bounds = GeoBounds(north = north, south = south, east = east, west = west)
        )

    private fun spot(id: String, kind: PoiKind, lat: Double, lng: Double, name: String? = null) =
        PointOfInterest(id, kind, name, Coordinate(lat, lng))

    // ---------------------------------------------------------------- nothing known yet

    @Test
    fun `with no places cached nothing is claimed either way`() {
        val stats = calculate(cellsAt(40.3770 to 49.8320, 40.3771 to 49.8321))

        assertEquals(0, stats.distinctParks)
        assertEquals(0, stats.distinctMonuments)
        assertEquals(0, stats.seasideCells)
        // Not 100%: an empty cache means the question has not been asked yet, not that the player
        // has walked all of a coastline that does not exist.
        assertEquals(0.0, stats.coastlineCoveragePercent, 0.001)
        assertEquals(0.0, stats.parkCoveragePercent, 0.001)
    }

    @Test
    fun `a cached place nowhere near the player leaves everything at zero`() {
        val stats = calculate(
            cells = cellsAt(40.3770 to 49.8320),
            pois = listOf(spot("node/1", PoiKind.MONUMENT, 41.0, 50.0))
        )

        assertEquals(0, stats.distinctMonuments)
    }

    // ---------------------------------------------------------------- places visited

    @Test
    fun `each park walked in is counted once, however many cells fell inside it`() {
        val parks = listOf(
            park("way/1", "Dənizkənarı", north = 40.378, south = 40.376, east = 49.834, west = 49.832),
            park("way/2", "Fəvvarələr", north = 40.373, south = 40.371, east = 49.839, west = 49.837)
        )
        val stats = calculate(
            cells = cellsAt(
                40.377 to 49.833,
                40.3772 to 49.8332,
                40.3774 to 49.8334,
                40.372 to 49.838
            ),
            pois = parks
        )

        assertEquals(2, stats.distinctParks)
    }

    @Test
    fun `the park coverage is the share of the known parks that were walked in`() {
        val parks = listOf(
            park("way/1", "A", north = 40.378, south = 40.376, east = 49.834, west = 49.832),
            park("way/2", "B", north = 40.373, south = 40.371, east = 49.839, west = 49.837),
            park("way/3", "C", north = 40.363, south = 40.361, east = 49.849, west = 49.847),
            park("way/4", "D", north = 40.353, south = 40.351, east = 49.859, west = 49.857)
        )
        val stats = calculate(
            cells = cellsAt(40.377 to 49.833, 40.372 to 49.838),
            pois = parks
        )

        assertEquals(2, stats.distinctParks)
        assertEquals(50.0, stats.parkCoveragePercent, 0.001)
    }

    @Test
    fun `monuments, metro, bridges and squares are counted separately`() {
        val stats = calculate(
            cells = cellsAt(40.3770 to 49.8320),
            pois = listOf(
                spot("node/1", PoiKind.MONUMENT, 40.3770, 49.8320),
                spot("node/2", PoiKind.METRO, 40.3770, 49.8320),
                spot("way/3", PoiKind.BRIDGE, 40.3770, 49.8320),
                spot("way/4", PoiKind.SQUARE, 40.3770, 49.8320)
            )
        )

        assertEquals(1, stats.distinctMonuments)
        assertEquals(1, stats.distinctMetroStations)
        assertEquals(1, stats.distinctBridges)
        assertEquals(1, stats.distinctSquares)
    }

    @Test
    fun `walking the length of one named bridge is still one bridge`() {
        val stats = calculate(
            cells = cellsAt(40.3770 to 49.8320, 40.3772 to 49.8322, 40.3774 to 49.8324),
            pois = listOf(
                spot("way/1", PoiKind.BRIDGE, 40.3770, 49.8320, name = "Böyük körpü"),
                spot("way/2", PoiKind.BRIDGE, 40.3772, 49.8322, name = "Böyük körpü"),
                spot("way/3", PoiKind.BRIDGE, 40.3774, 49.8324, name = "Böyük körpü")
            )
        )

        assertEquals(1, stats.distinctBridges)
    }

    // ---------------------------------------------------------------- the coast

    @Test
    fun `seaside is counted in cells and the coastline in how much of it was covered`() {
        val coast = (0..3).map { i ->
            spot("way/9@$i", PoiKind.COAST, 40.3770 + i * 0.002, 49.8320)
        }
        // The first two pieces of coast are walked; the other two are not.
        val stats = calculate(
            cells = cellsAt(40.3770 to 49.8320, 40.3790 to 49.8320, 40.3900 to 49.9000),
            pois = coast
        )

        assertEquals(2, stats.seasideCells)
        assertEquals(50.0, stats.coastlineCoveragePercent, 0.001)
    }

    @Test
    fun `walking every known piece of coast completes it`() {
        val coast = (0..2).map { i ->
            spot("way/9@$i", PoiKind.COAST, 40.3770 + i * 0.002, 49.8320)
        }
        val stats = calculate(
            cells = cellsAt(40.3770 to 49.8320, 40.3790 to 49.8320, 40.3810 to 49.8320),
            pois = coast
        )

        assertEquals(100.0, stats.coastlineCoveragePercent, 0.001)
    }

    // ---------------------------------------------------------------- the town

    private val baku = CityBounds(
        city = "Bakı",
        countryCode = "AZ",
        bounds = GeoBounds(north = 40.50, south = 40.30, east = 50.00, west = 49.70),
        center = Coordinate(40.40, 49.85)
    )

    @Test
    fun `cells near the middle of town count towards the centre`() {
        val stats = calculate(
            cells = cellsAt(40.400 to 49.850, 40.405 to 49.855, 40.395 to 49.845),
            cityBounds = listOf(baku)
        )

        assertEquals(3, stats.cityCentreCells)
        assertFalse(stats.hasReachedCityEdge)
    }

    @Test
    fun `a cell out by the boundary reaches the edge`() {
        val stats = calculate(
            cells = cellsAt(40.400 to 49.850, 40.495 to 49.850),
            cityBounds = listOf(baku)
        )

        assertEquals(1, stats.cityCentreCells)
        assertTrue(stats.hasReachedCityEdge)
    }

    @Test
    fun `cells outside every known town are attributed to none of them`() {
        val stats = calculate(
            cells = cellsAt(41.700 to 46.350),
            cityBounds = listOf(baku)
        )

        assertEquals(0, stats.cityCentreCells)
        assertFalse(stats.hasReachedCityEdge)
    }

    @Test
    fun `a town Nominatim could not place is ignored rather than treated as everywhere`() {
        val unplaceable = CityBounds("Bilinməyən", "AZ", bounds = null, center = null)

        val stats = calculate(
            cells = cellsAt(40.400 to 49.850),
            cityBounds = listOf(unplaceable)
        )

        assertEquals(0, stats.cityCentreCells)
        assertFalse(stats.hasReachedCityEdge)
    }

    /**
     * The centre is the town's recognised centre, not the middle of its box. For a coastal city
     * those are far apart, and measuring from the box would put the centre out in the sea.
     */
    @Test
    fun `the centre is measured from the town's own centre point, not its bounding box`() {
        val coastal = baku.copy(center = Coordinate(40.35, 49.75))

        val stats = calculate(
            cells = cellsAt(40.350 to 49.750, 40.400 to 49.850),
            cityBounds = listOf(coastal)
        )

        // Only the cell at the real centre; the middle of the box is well outside it.
        assertEquals(1, stats.cityCentreCells)
    }
}
