package com.example.domain.usecase

import com.example.domain.model.CellContext
import com.example.domain.model.Coordinate
import com.example.domain.model.PlaceInfo
import com.example.domain.model.StompedHex
import com.example.domain.repository.GeocodingRepository
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePlaceCellRepository(private var pending: List<String>) : StompedHexRepository {
    val written = mutableMapOf<String, PlaceInfo>()
    var lastSkip: Set<String> = emptySet()
    var lastLimit: Int? = null

    override val stompedHexes: Flow<List<StompedHex>> = MutableStateFlow(emptyList())
    override suspend fun getAll(): List<StompedHex> = emptyList()
    override suspend fun stomp(hexAddress: String, neighborhood: String?, context: CellContext?) {}
    override suspend fun stompAll(
        hexAddresses: List<String>,
        neighborhood: String?,
        context: CellContext?
    ) {}

    override suspend fun cellsMissingElevation(limit: Int): List<String> = emptyList()
    override suspend fun setElevations(elevations: Map<String, Double>) {}

    override suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String> {
        lastSkip = skip
        lastLimit = limit
        return pending.filterNot { it in skip || it in written }.take(limit)
    }

    override suspend fun setPlaces(places: Map<String, PlaceInfo>) {
        written.putAll(places)
    }

    override suspend fun markPartiallyExplored(
        hexAddresses: List<String>,
        level: Float,
        neighborhood: String?
    ) {}

    override suspend fun unstomp(hexAddress: String) {}
    override suspend fun clearAll() {}
}

private class FakeGeocoder(private val answer: (Double, Double) -> PlaceInfo?) : GeocodingRepository {
    var calls = 0
        private set

    override suspend fun reverseGeocode(lat: Double, lng: Double): PlaceInfo? {
        calls++
        return answer(lat, lng)
    }
}

class EnrichCellPlacesUseCaseTest {

    /** Cell ids map to distinct points; "nowhere" resolves to no geometry at all. */
    private val centers: (String) -> Coordinate? = { id ->
        if (id == "nowhere") null else Coordinate(40.0 + id.hashCode() % 5, 49.0)
    }

    @Test
    fun `a batch is geocoded and written back`() = runTest {
        val cells = FakePlaceCellRepository(listOf("a", "b"))
        val geocoder = FakeGeocoder { _, _ -> PlaceInfo("Nərimanov", "Bakı", "AZ") }

        val filled = EnrichCellPlacesUseCase(cells, geocoder, centers)()

        assertEquals(2, filled)
        assertEquals(setOf("a", "b"), cells.written.keys)
        assertEquals("Bakı", cells.written["a"]?.city)
        assertEquals("AZ", cells.written["a"]?.countryCode)
    }

    @Test
    fun `a country with no town is still worth storing`() = runTest {
        val cells = FakePlaceCellRepository(listOf("rural"))
        val geocoder = FakeGeocoder { _, _ -> PlaceInfo(city = null, countryCode = "AZ") }

        assertEquals(1, EnrichCellPlacesUseCase(cells, geocoder, centers)())
        assertEquals("AZ", cells.written["rural"]?.countryCode)
    }

    @Test
    fun `nothing pending means the geocoder is never asked`() = runTest {
        val cells = FakePlaceCellRepository(emptyList())
        val geocoder = FakeGeocoder { _, _ -> PlaceInfo(city = "Bakı") }

        assertEquals(0, EnrichCellPlacesUseCase(cells, geocoder, centers)())
        assertEquals(0, geocoder.calls)
    }

    @Test
    fun `a cell the geocoder cannot name is skipped next time round`() = runTest {
        val cells = FakePlaceCellRepository(listOf("sea", "a"))
        val geocoder = FakeGeocoder { lat, _ ->
            // Only the second cell has a name; the first is out at sea.
            if (lat == centers("a")!!.lat) PlaceInfo(city = "Bakı") else null
        }
        val useCase = EnrichCellPlacesUseCase(cells, geocoder, centers)

        useCase()
        useCase()

        // Having proved the geocoder works, the nameless cell is written off rather than sitting at
        // the head of the queue forever.
        assertTrue("sea" in cells.lastSkip)
    }

    @Test
    fun `an offline geocoder writes nothing off`() = runTest {
        val cells = FakePlaceCellRepository(listOf("a", "b"))
        val geocoder = FakeGeocoder { _, _ -> null }
        val useCase = EnrichCellPlacesUseCase(cells, geocoder, centers)

        assertEquals(0, useCase())
        useCase()

        // A whole batch resolving nothing is far more likely to be an outage than genuinely
        // nameless ground, so the cells stay in the queue for a later attempt.
        assertTrue(cells.lastSkip.isEmpty())
        assertTrue(cells.written.isEmpty())
    }

    @Test
    fun `a geocoder that throws does not take the batch down with it`() = runTest {
        val cells = FakePlaceCellRepository(listOf("a"))
        val geocoder = FakeGeocoder { _, _ -> throw RuntimeException("service died") }

        assertEquals(0, EnrichCellPlacesUseCase(cells, geocoder, centers)())
        assertTrue(cells.written.isEmpty())
    }

    @Test
    fun `cells with no geometry are written off immediately`() = runTest {
        val cells = FakePlaceCellRepository(listOf("nowhere", "a"))
        val geocoder = FakeGeocoder { _, _ -> PlaceInfo(city = "Bakı") }
        val useCase = EnrichCellPlacesUseCase(cells, geocoder, centers)

        useCase()
        useCase()

        // No point to ask about, and a retry cannot change that - unlike a failed lookup.
        assertTrue("nowhere" in cells.lastSkip)
        assertEquals(setOf("a"), cells.written.keys)
    }
}
