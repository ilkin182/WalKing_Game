package com.example.data.repository

import app.cash.turbine.test
import com.example.data.local.dao.StompedHexDao
import com.example.data.local.entity.StompedHexEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** In-memory fake DAO: no Room/SQLite instance needed, keeps the test a pure JVM unit test. */
private class FakeStompedHexDao : StompedHexDao {
    private val storedHexes = MutableStateFlow<List<StompedHexEntity>>(emptyList())

    override fun getAllStompedHexesFlow(): Flow<List<StompedHexEntity>> = storedHexes.asStateFlow()
    override suspend fun getAllStompedHexes(): List<StompedHexEntity> = storedHexes.value

    override suspend fun insertHex(hex: StompedHexEntity) {
        storedHexes.value = storedHexes.value.filterNot { it.hexAddress == hex.hexAddress } + hex
    }

    override suspend fun insertHexes(hexes: List<StompedHexEntity>) {
        val addresses = hexes.map { it.hexAddress }.toSet()
        storedHexes.value = storedHexes.value.filterNot { it.hexAddress in addresses } + hexes
    }

    override suspend fun cellsMissingElevation(limit: Int): List<String> =
        storedHexes.value.filter { it.elevationM == null }.sortedBy { it.timestamp }
            .take(limit).map { it.hexAddress }

    override suspend fun cellsMissingPlace(limit: Int, skip: List<String>): List<String> =
        storedHexes.value.filter { it.city == null && it.countryCode == null && it.hexAddress !in skip }
            .sortedBy { it.timestamp }.take(limit).map { it.hexAddress }

    override suspend fun setPlace(hexAddress: String, city: String?, countryCode: String?) {
        storedHexes.value = storedHexes.value.map {
            if (it.hexAddress == hexAddress) it.copy(city = city, countryCode = countryCode) else it
        }
    }

    override suspend fun setElevation(hexAddress: String, elevationMeters: Double) {
        storedHexes.value = storedHexes.value.map {
            if (it.hexAddress == hexAddress) it.copy(elevationM = elevationMeters) else it
        }
    }

    override suspend fun insertHexesIfAbsent(hexes: List<StompedHexEntity>) {
        val existing = storedHexes.value.mapTo(mutableSetOf()) { it.hexAddress }
        storedHexes.value = storedHexes.value + hexes.filterNot { it.hexAddress in existing }
    }

    override suspend fun raiseExplorationLevel(
        hexAddresses: List<String>,
        level: Float,
        timestamp: Long
    ): Int {
        var updated = 0
        storedHexes.value = storedHexes.value.map { hex ->
            if (hex.hexAddress in hexAddresses && hex.explorationLevel < level) {
                updated++
                hex.copy(explorationLevel = level, timestamp = timestamp)
            } else {
                hex
            }
        }
        return updated
    }

    override suspend fun deleteHex(hexAddress: String) {
        storedHexes.value = storedHexes.value.filterNot { it.hexAddress == hexAddress }
    }

    override suspend fun clearAll() {
        storedHexes.value = emptyList()
    }
}

class StompedHexRepositoryImplTest {
    private lateinit var dao: FakeStompedHexDao
    private lateinit var repository: StompedHexRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeStompedHexDao()
        repository = StompedHexRepositoryImpl(dao)
    }

    @Test
    fun `stomp maps to a domain hex observable through the flow`() = runTest {
        repository.stompedHexes.test {
            assertEquals(emptyList<Any>(), awaitItem())

            repository.stomp("cell_a", "Downtown")

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("cell_a", updated.single().hexAddress)
            assertEquals("Downtown", updated.single().neighborhood)
        }
    }

    @Test
    fun `stompAll inserts every address with the same neighborhood`() = runTest {
        repository.stompAll(listOf("a", "b", "c"), "Uptown")

        val all = repository.getAll()
        assertEquals(3, all.size)
        assertTrue(all.all { it.neighborhood == "Uptown" })
    }

    @Test
    fun `markPartiallyExplored records cells that are not known yet`() = runTest {
        repository.markPartiallyExplored(listOf("a", "b"), level = 0.5f, neighborhood = "Uptown")

        val all = repository.getAll()
        assertEquals(setOf("a", "b"), all.map { it.hexAddress }.toSet())
        assertTrue(all.all { it.explorationLevel == 0.5f })
    }

    @Test
    fun `markPartiallyExplored never re-fogs a cell the player walked into`() = runTest {
        repository.stompAll(listOf("walked"), "Uptown")

        repository.markPartiallyExplored(listOf("walked"), level = 0.5f)

        assertEquals(1.0f, repository.getAll().single().explorationLevel, 1e-6f)
    }

    @Test
    fun `markPartiallyExplored raises a cell that was only glimpsed before`() = runTest {
        repository.markPartiallyExplored(listOf("a"), level = 0.25f)

        repository.markPartiallyExplored(listOf("a"), level = 0.75f)

        assertEquals(0.75f, repository.getAll().single().explorationLevel, 1e-6f)
    }

    @Test
    fun `markPartiallyExplored with nothing to record touches no rows`() = runTest {
        repository.markPartiallyExplored(emptyList(), level = 0.5f)

        assertTrue(repository.getAll().isEmpty())
    }

    @Test
    fun `unstomp removes a single hex`() = runTest {
        repository.stomp("a")
        repository.stomp("b")

        repository.unstomp("a")

        val all = repository.getAll()
        assertEquals(listOf("b"), all.map { it.hexAddress })
    }

    @Test
    fun `clearAll empties the store`() = runTest {
        repository.stompAll(listOf("a", "b"))

        repository.clearAll()

        assertTrue(repository.getAll().isEmpty())
    }
}
