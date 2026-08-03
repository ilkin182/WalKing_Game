package com.example.domain.usecase

import com.example.domain.engine.PoiTiles
import com.example.domain.model.CellContext
import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.PlaceInfo
import com.example.domain.model.PointOfInterest
import com.example.domain.model.StompedHex
import com.example.domain.repository.PoiRepository
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two passes that fill the points-of-interest cache behind the game.
 *
 * Both are queues over the player's own history, and both have the same job: work out the smallest
 * set of questions that would answer the geography achievements, and ask them as slowly as possible.
 */
class EnrichPoiTilesUseCaseTest {

    private class FakeCells : StompedHexRepository {
        val rows = MutableStateFlow<List<StompedHex>>(emptyList())
        override val stompedHexes: Flow<List<StompedHex>> = rows
        override suspend fun getAll(): List<StompedHex> = rows.value
        override suspend fun stomp(hexAddress: String, neighborhood: String?, context: CellContext?) {}
        override suspend fun stompAll(
            hexAddresses: List<String>,
            neighborhood: String?,
            context: CellContext?
        ) {}
        override suspend fun cellsMissingElevation(limit: Int): List<String> = emptyList()
        override suspend fun setElevations(elevations: Map<String, Double>) {}
        override suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String> = emptyList()
        override suspend fun setPlaces(places: Map<String, PlaceInfo>) {}
        override suspend fun markPartiallyExplored(
            hexAddresses: List<String>,
            level: Float,
            neighborhood: String?
        ) {}
        override suspend fun unstomp(hexAddress: String) {}
        override suspend fun clearAll() {
            rows.value = emptyList()
        }
    }

    private class FakePois(var succeeds: Boolean = true) : PoiRepository {
        val fetched = mutableListOf<String>()
        val lookedUp = mutableListOf<Pair<String, String?>>()
        val cached = mutableSetOf<String>()
        val cities = mutableSetOf<String>()

        override val pois: Flow<List<PointOfInterest>> = MutableStateFlow(emptyList())
        override val cityBounds: Flow<List<CityBounds>> = MutableStateFlow(emptyList())

        override suspend fun cachedTiles(tileKeys: List<String>): Set<String> =
            tileKeys.filter { it in cached }.toSet()

        override suspend fun fetchTile(tileKey: String): Boolean {
            fetched += tileKey
            if (succeeds) cached += tileKey
            return succeeds
        }

        override suspend fun knownCities(): Set<String> = cities

        override suspend fun fetchCityBounds(city: String, countryCode: String?): Boolean {
            lookedUp += city to countryCode
            if (succeeds) cities += city
            return succeeds
        }

        override suspend fun clearAll() {}
    }

    /** Cell ids are their own coordinates, so a test's geography is readable where it is written. */
    private fun centerOf(cellId: String): Coordinate? {
        val parts = cellId.split(',')
        if (parts.size != 2) return null
        return Coordinate(parts[0].toDouble(), parts[1].toDouble())
    }

    private fun cell(lat: Double, lng: Double, at: Long, city: String? = null, country: String? = null) =
        StompedHex(
            hexAddress = "$lat,$lng",
            neighborhood = null,
            timestamp = at,
            context = if (city == null) null else CellContext(city = city, countryCode = country)
        )

    private fun tiles(cells: FakeCells, pois: FakePois) =
        EnrichPoiTilesUseCase(cells, pois, ::centerOf)

    // ---------------------------------------------------------------- tiles

    @Test
    fun `an empty history asks nothing`() = runTest {
        val cells = FakeCells()
        val pois = FakePois()

        assertEquals(0, tiles(cells, pois)())

        assertTrue(pois.fetched.isEmpty())
    }

    /**
     * The whole point of the tile grid. A walk through one square claims dozens of cells, and they
     * have to cost one question between them rather than one each.
     */
    @Test
    fun `many cells in one square are one request`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(
            cell(40.3771, 49.8321, at = 1),
            cell(40.3775, 49.8325, at = 2),
            cell(40.3779, 49.8329, at = 3)
        )
        val pois = FakePois()

        assertEquals(1, tiles(cells, pois)(limit = 10))

        assertEquals(listOf(PoiTiles.keyOf(40.3771, 49.8321)), pois.fetched)
    }

    @Test
    fun `tiles already cached are not asked about again`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(cell(40.3771, 49.8321, at = 1), cell(40.3871, 49.8321, at = 2))
        val pois = FakePois()
        pois.cached += PoiTiles.keyOf(40.3771, 49.8321)

        tiles(cells, pois)(limit = 10)

        assertEquals(listOf(PoiTiles.keyOf(40.3871, 49.8321)), pois.fetched)
    }

    @Test
    fun `the oldest ground is asked about first`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(
            cell(40.3871, 49.8321, at = 200),
            cell(40.3771, 49.8321, at = 100)
        )
        val pois = FakePois()

        tiles(cells, pois)(limit = 1)

        assertEquals(listOf(PoiTiles.keyOf(40.3771, 49.8321)), pois.fetched)
    }

    @Test
    fun `only one square is asked about per pass by default`() = runTest {
        val cells = FakeCells()
        cells.rows.value = (0..9).map { cell(40.3771 + it * 0.01, 49.8321, at = it.toLong()) }
        val pois = FakePois()

        assertEquals(1, tiles(cells, pois)())

        assertEquals(1, pois.fetched.size)
    }

    /**
     * A tile that could not be fetched must not be reported as done: the caller's drain loop stops
     * as soon as a pass fills nothing, and reporting a failure as work would spin it.
     */
    @Test
    fun `a square that could not be fetched does not count as done`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(cell(40.3771, 49.8321, at = 1))
        val pois = FakePois(succeeds = false)

        assertEquals(0, tiles(cells, pois)())

        assertEquals(1, pois.fetched.size)
    }

    @Test
    fun `cells whose geometry cannot be resolved are skipped`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(
            StompedHex("not-a-coordinate", null, 1L),
            cell(40.3771, 49.8321, at = 2)
        )
        val pois = FakePois()

        assertEquals(1, tiles(cells, pois)(limit = 10))

        assertEquals(listOf(PoiTiles.keyOf(40.3771, 49.8321)), pois.fetched)
    }

    /** Draining a backlog runs for hours; recomputing the queue each time would resolve every cell. */
    @Test
    fun `the queue is reused until the history changes`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(cell(40.3771, 49.8321, at = 1), cell(40.3871, 49.8321, at = 2))
        val pois = FakePois()
        var resolved = 0
        val useCase = EnrichPoiTilesUseCase(cells, pois) { id -> resolved++; centerOf(id) }

        useCase()
        val afterFirst = resolved
        useCase()
        assertEquals(afterFirst, resolved)

        cells.rows.value = cells.rows.value + cell(40.3971, 49.8321, at = 3)
        useCase()

        assertEquals(afterFirst + 3, resolved)
    }

    // ---------------------------------------------------------------- towns

    @Test
    fun `each town the player walked in is looked up exactly once, oldest first`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(
            cell(40.5771, 49.6321, at = 3, city = "Sumqayıt", country = "AZ"),
            cell(40.3771, 49.8321, at = 1, city = "Bakı", country = "AZ"),
            cell(40.3775, 49.8325, at = 2, city = "Bakı", country = "AZ")
        )
        val pois = FakePois()
        val useCase = EnrichCityBoundsUseCase(cells, pois)

        assertEquals(1, useCase())
        assertEquals(1, useCase(limit = 5))
        assertEquals(0, useCase(limit = 5))

        // The town the player started in first, and the one they walked in twice only once.
        assertEquals(listOf("Bakı" to "AZ", "Sumqayıt" to "AZ"), pois.lookedUp)
    }

    @Test
    fun `a town already looked up is left alone`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(cell(40.3771, 49.8321, at = 1, city = "Bakı", country = "AZ"))
        val pois = FakePois()
        pois.cities += "Bakı"

        assertEquals(0, EnrichCityBoundsUseCase(cells, pois)())

        assertTrue(pois.lookedUp.isEmpty())
    }

    @Test
    fun `cells with no town recorded contribute nothing to look up`() = runTest {
        val cells = FakeCells()
        cells.rows.value = listOf(cell(40.3771, 49.8321, at = 1))
        val pois = FakePois()

        assertEquals(0, EnrichCityBoundsUseCase(cells, pois)())

        assertTrue(pois.lookedUp.isEmpty())
    }
}
