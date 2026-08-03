package com.example.data.repository

import com.example.data.local.dao.PoiDao
import com.example.data.local.entity.CityBoundsEntity
import com.example.data.local.entity.PoiEntity
import com.example.data.local.entity.PoiTileEntity
import com.example.data.mapper.toDomain
import com.example.data.remote.NominatimApi
import com.example.data.remote.NominatimPlaceDto
import com.example.data.remote.OverpassApi
import com.example.data.remote.OverpassBounds
import com.example.data.remote.OverpassElement
import com.example.data.remote.OverpassResponse
import com.example.domain.engine.PoiTiles
import com.example.domain.model.PoiKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The cache, and the manners.
 *
 * Overpass and Nominatim run on donated hardware with no API key to throttle per account, so the
 * only thing standing between this app and being blocked is that it asks each square once and leaves
 * a gap between requests. Those two properties are what most of these tests are about; the rest is
 * making sure a failure does not get written down as an answer.
 */
class PoiRepositoryImplTest {

    private class FakePoiDao : PoiDao {
        val pois = MutableStateFlow<List<PoiEntity>>(emptyList())
        val cities = MutableStateFlow<List<CityBoundsEntity>>(emptyList())
        val tiles = mutableMapOf<String, Long>()

        override fun observeAll(): Flow<List<PoiEntity>> = pois

        override suspend fun cachedTiles(tileKeys: List<String>, freshAfter: Long): List<String> =
            tileKeys.filter { (tiles[it] ?: Long.MIN_VALUE) > freshAfter }

        override suspend fun insertPois(pois: List<PoiEntity>) {
            val byId = this.pois.value.associateBy { it.id }.toMutableMap()
            pois.forEach { byId[it.id] = it }
            this.pois.value = byId.values.toList()
        }

        override suspend fun markTileFetched(tile: PoiTileEntity) {
            tiles[tile.tileKey] = tile.fetchedAt
        }

        override suspend fun deletePoisOfTile(tileKey: String) {
            pois.value = pois.value.filterNot { it.tileKey == tileKey }
        }

        override fun observeCityBounds(): Flow<List<CityBoundsEntity>> = cities

        override suspend fun knownCities(): List<String> = cities.value.map { it.city }

        override suspend fun insertCityBounds(bounds: CityBoundsEntity) {
            cities.value = cities.value.filterNot { it.city == bounds.city } + bounds
        }

        override suspend fun clearPois() {
            pois.value = emptyList()
        }

        override suspend fun clearTiles() {
            tiles.clear()
        }

        override suspend fun clearCityBounds() {
            cities.value = emptyList()
        }
    }

    private class RecordingOverpass(
        private var answer: () -> OverpassResponse = { OverpassResponse(emptyList()) }
    ) : OverpassApi {
        val queries = mutableListOf<String>()

        fun answersWith(answer: () -> OverpassResponse) {
            this.answer = answer
        }

        override suspend fun query(query: String): OverpassResponse {
            queries += query
            return answer()
        }
    }

    private class RecordingNominatim(
        private var answer: () -> List<NominatimPlaceDto> = { emptyList() }
    ) : NominatimApi {
        val searches = mutableListOf<Pair<String, String?>>()

        fun answersWith(answer: () -> List<NominatimPlaceDto>) {
            this.answer = answer
        }

        override suspend fun search(
            place: String,
            countryCodes: String?,
            format: String,
            limit: Int
        ): List<NominatimPlaceDto> {
            searches += place to countryCodes
            return answer()
        }
    }

    private val tileKey = PoiTiles.keyOf(40.377, 49.832)

    private class Fixture {
        val dao = FakePoiDao()
        val overpass = RecordingOverpass()
        val nominatim = RecordingNominatim()
        var clock = 1_000_000L
        val waits = mutableListOf<Long>()

        val repository = PoiRepositoryImpl(
            dao = dao,
            overpass = overpass,
            nominatim = nominatim,
            now = { clock },
            // The gap is verified by what it asks to wait for, so the test never actually sleeps.
            pause = { waits += it; clock += it }
        )
    }

    private fun park(id: Long) = OverpassElement(
        type = "way",
        id = id,
        bounds = OverpassBounds(minLat = 40.376, minLon = 49.831, maxLat = 40.378, maxLon = 49.833),
        tags = mapOf("leisure" to "park", "name" to "Park $id")
    )

    // ---------------------------------------------------------------- the tile cache

    @Test
    fun `a fetched tile is stored with its places`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { OverpassResponse(listOf(park(1))) }

        assertTrue(f.repository.fetchTile(tileKey))

        assertEquals(setOf(tileKey), f.repository.cachedTiles(listOf(tileKey)))
        assertEquals(1, f.dao.pois.value.size)
        assertEquals(PoiKind.PARK.name, f.dao.pois.value.single().kind)
        assertEquals(tileKey, f.dao.pois.value.single().tileKey)
    }

    /**
     * The one that matters most. A square of plain streets with no park and no bridge in it comes
     * back empty, and if that were not written down it would be asked about again forever.
     */
    @Test
    fun `an empty tile is remembered as an answer, not as a missing one`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { OverpassResponse(emptyList()) }

        assertTrue(f.repository.fetchTile(tileKey))

        assertTrue(f.dao.pois.value.isEmpty())
        assertEquals(setOf(tileKey), f.repository.cachedTiles(listOf(tileKey)))
    }

    @Test
    fun `a failed fetch leaves the tile to be tried again`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { throw IOException("offline") }

        assertFalse(f.repository.fetchTile(tileKey))

        assertTrue(f.repository.cachedTiles(listOf(tileKey)).isEmpty())
    }

    @Test
    fun `a tile whose key is not a real one is never sent`() = runTest {
        val f = Fixture()

        assertFalse(f.repository.fetchTile("nonsense"))

        assertTrue(f.overpass.queries.isEmpty())
    }

    @Test
    fun `a tile past its lifetime counts as needing a new answer`() = runTest {
        val f = Fixture()
        f.repository.fetchTile(tileKey)
        assertEquals(setOf(tileKey), f.repository.cachedTiles(listOf(tileKey)))

        f.clock += PoiRepositoryImpl.DEFAULT_TILE_TTL_MILLIS + 1

        assertTrue(f.repository.cachedTiles(listOf(tileKey)).isEmpty())
    }

    @Test
    fun `refetching a tile replaces what it held rather than piling up`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { OverpassResponse(listOf(park(1), park(2))) }
        f.repository.fetchTile(tileKey)
        assertEquals(2, f.dao.pois.value.size)

        f.overpass.answersWith { OverpassResponse(listOf(park(1))) }
        f.repository.fetchTile(tileKey)

        assertEquals(1, f.dao.pois.value.size)
    }

    @Test
    fun `asking about no tiles at all does not touch the database`() = runTest {
        val f = Fixture()

        assertTrue(f.repository.cachedTiles(emptyList()).isEmpty())
    }

    // ---------------------------------------------------------------- the gap between requests

    @Test
    fun `the first request goes out without waiting`() = runTest {
        val f = Fixture()

        f.repository.fetchTile(tileKey)

        assertTrue(f.waits.isEmpty())
    }

    @Test
    fun `back to back requests are spaced out`() = runTest {
        val f = Fixture()

        f.repository.fetchTile(tileKey)
        f.repository.fetchTile(PoiTiles.keyOf(40.387, 49.832))
        f.repository.fetchCityBounds("Bakı", "AZ")

        assertEquals(2, f.waits.size)
        f.waits.forEach { assertEquals(PoiRepositoryImpl.DEFAULT_REQUEST_GAP_MILLIS, it) }
    }

    @Test
    fun `a request that comes long after the last one does not wait`() = runTest {
        val f = Fixture()
        f.repository.fetchTile(tileKey)

        f.clock += PoiRepositoryImpl.DEFAULT_REQUEST_GAP_MILLIS * 2
        f.repository.fetchTile(PoiTiles.keyOf(40.387, 49.832))

        assertTrue(f.waits.isEmpty())
    }

    /** An outage must not turn into a retry loop: a failure costs the same gap as a success. */
    @Test
    fun `a failed request still holds the next one back`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { throw IOException("offline") }

        f.repository.fetchTile(tileKey)
        f.repository.fetchTile(PoiTiles.keyOf(40.387, 49.832))

        assertEquals(listOf(PoiRepositoryImpl.DEFAULT_REQUEST_GAP_MILLIS), f.waits)
    }

    // ---------------------------------------------------------------- towns

    @Test
    fun `a town's extent is stored with its centre`() = runTest {
        val f = Fixture()
        f.nominatim.answersWith {
            listOf(
                NominatimPlaceDto(
                    lat = "40.4093",
                    lon = "49.8671",
                    boundingBox = listOf("40.30", "40.50", "49.70", "50.00")
                )
            )
        }

        assertTrue(f.repository.fetchCityBounds("Bakı", "AZ"))

        val stored = f.dao.cities.value.single()
        assertTrue(stored.found)
        assertEquals(40.50, stored.north!!, 1e-9)
        assertEquals(40.30, stored.south!!, 1e-9)
        assertEquals(49.70, stored.west!!, 1e-9)
        assertEquals(50.00, stored.east!!, 1e-9)
        assertEquals(40.4093, stored.centerLat!!, 1e-9)
        assertEquals(listOf("Bakı" to "az"), f.nominatim.searches)
    }

    /** Same reasoning as the empty tile: a name with no answer must not be asked about forever. */
    @Test
    fun `a town Nominatim cannot place is written down as not found`() = runTest {
        val f = Fixture()
        f.nominatim.answersWith { emptyList() }

        assertTrue(f.repository.fetchCityBounds("Bilinməyən", "AZ"))

        val stored = f.dao.cities.value.single()
        assertFalse(stored.found)
        assertNull(stored.north)
        assertEquals(setOf("Bilinməyən"), f.repository.knownCities())
    }

    @Test
    fun `half an answer is stored as no answer`() = runTest {
        val f = Fixture()
        // A centre but no box: the city achievements measure a position against both.
        f.nominatim.answersWith { listOf(NominatimPlaceDto(lat = "40.4", lon = "49.8")) }

        f.repository.fetchCityBounds("Bakı", "AZ")

        assertFalse(f.dao.cities.value.single().found)
    }

    @Test
    fun `a failed lookup is not written down at all`() = runTest {
        val f = Fixture()
        f.nominatim.answersWith { throw IOException("offline") }

        assertFalse(f.repository.fetchCityBounds("Bakı", "AZ"))

        assertTrue(f.dao.cities.value.isEmpty())
    }

    @Test
    fun `a blank town name is never sent`() = runTest {
        val f = Fixture()

        assertFalse(f.repository.fetchCityBounds("   ", "AZ"))

        assertTrue(f.nominatim.searches.isEmpty())
    }

    // ---------------------------------------------------------------- reading back

    @Test
    fun `the cached places come back as places`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { OverpassResponse(listOf(park(1))) }
        f.repository.fetchTile(tileKey)

        val poi = f.dao.pois.value.single().toDomain()

        assertNotNull(poi)
        assertEquals(PoiKind.PARK, poi!!.kind)
        assertEquals("Park 1", poi.name)
        assertNotNull(poi.bounds)
    }

    @Test
    fun `clearing drops the places, the tiles and the towns together`() = runTest {
        val f = Fixture()
        f.overpass.answersWith { OverpassResponse(listOf(park(1))) }
        f.repository.fetchTile(tileKey)
        f.nominatim.answersWith { emptyList() }
        f.repository.fetchCityBounds("Bakı", "AZ")

        f.repository.clearAll()

        assertTrue(f.dao.pois.value.isEmpty())
        assertTrue(f.dao.cities.value.isEmpty())
        assertTrue(f.repository.cachedTiles(listOf(tileKey)).isEmpty())
    }
}
