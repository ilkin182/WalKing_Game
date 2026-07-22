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
