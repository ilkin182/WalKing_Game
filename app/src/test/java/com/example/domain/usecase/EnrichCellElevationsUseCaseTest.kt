package com.example.domain.usecase

import com.example.domain.model.CellContext
import com.example.domain.model.Coordinate
import com.example.domain.model.PlaceInfo
import com.example.domain.model.StompedHex
import com.example.domain.repository.ElevationRepository
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCellRepository(pending: List<String>) : StompedHexRepository {
    var pendingCells = pending
    val written = mutableMapOf<String, Double>()
    var lastLimit: Int? = null

    override val stompedHexes: Flow<List<StompedHex>> = MutableStateFlow(emptyList())
    override suspend fun getAll(): List<StompedHex> = emptyList()
    override suspend fun stomp(hexAddress: String, neighborhood: String?, context: CellContext?) {}
    override suspend fun stompAll(
        hexAddresses: List<String>,
        neighborhood: String?,
        context: CellContext?
    ) {}

    override suspend fun cellsMissingElevation(limit: Int): List<String> {
        lastLimit = limit
        return pendingCells.take(limit)
    }

    override suspend fun setElevations(elevations: Map<String, Double>) {
        written.putAll(elevations)
    }

    override suspend fun cellsMissingPlace(limit: Int, skip: Set<String>): List<String> = emptyList()
    override suspend fun setPlaces(places: Map<String, PlaceInfo>) {}

    override suspend fun markPartiallyExplored(
        hexAddresses: List<String>,
        level: Float,
        neighborhood: String?
    ) {}

    override suspend fun unstomp(hexAddress: String) {}
    override suspend fun clearAll() {}
}

private class FakeElevationRepository(
    private val heights: (List<Coordinate>) -> List<Double>
) : ElevationRepository {
    var requests = 0
        private set
    var lastPoints: List<Coordinate> = emptyList()

    override suspend fun elevations(points: List<Coordinate>): List<Double> {
        requests++
        lastPoints = points
        return heights(points)
    }
}

class EnrichCellElevationsUseCaseTest {

    private fun centers(vararg ids: String): (String) -> Coordinate? {
        val known = ids.mapIndexed { i, id -> id to Coordinate(40.0 + i, 49.0 + i) }.toMap()
        return { known[it] }
    }

    @Test
    fun `a batch of pending cells is looked up and written back`() = runTest {
        val cells = FakeCellRepository(listOf("a", "b", "c"))
        val elevations = FakeElevationRepository { points -> points.indices.map { it * 10.0 } }

        val filled = EnrichCellElevationsUseCase(cells, elevations, centers("a", "b", "c"))()

        assertEquals(3, filled)
        assertEquals(mapOf("a" to 0.0, "b" to 10.0, "c" to 20.0), cells.written)
        assertEquals(1, elevations.requests)
    }

    @Test
    fun `nothing pending means no request at all`() = runTest {
        val cells = FakeCellRepository(emptyList())
        val elevations = FakeElevationRepository { emptyList() }

        val filled = EnrichCellElevationsUseCase(cells, elevations, centers())()

        assertEquals(0, filled)
        assertEquals(0, elevations.requests)
        assertTrue(cells.written.isEmpty())
    }

    @Test
    fun `cells whose geometry cannot be resolved are dropped from the batch`() = runTest {
        val cells = FakeCellRepository(listOf("a", "unknown", "c"))
        val elevations = FakeElevationRepository { points -> points.map { 100.0 } }

        val filled = EnrichCellElevationsUseCase(cells, elevations, centers("a", "c"))()

        // The unresolvable cell is not sent with a placeholder coordinate, which would record a
        // confidently wrong height for it.
        assertEquals(2, filled)
        assertEquals(setOf("a", "c"), cells.written.keys)
    }

    @Test
    fun `a response that does not line up is discarded rather than misaligned`() = runTest {
        val cells = FakeCellRepository(listOf("a", "b", "c"))
        val elevations = FakeElevationRepository { listOf(10.0) }

        val filled = EnrichCellElevationsUseCase(cells, elevations, centers("a", "b", "c"))()

        assertEquals(0, filled)
        assertTrue(cells.written.isEmpty())
    }

    @Test
    fun `a failed lookup leaves the queue for next time`() = runTest {
        val cells = FakeCellRepository(listOf("a"))
        val elevations = FakeElevationRepository { emptyList() }

        assertEquals(0, EnrichCellElevationsUseCase(cells, elevations, centers("a"))())
        assertTrue(cells.written.isEmpty())
    }

    @Test
    fun `the batch size is passed through to the query`() = runTest {
        val cells = FakeCellRepository(List(500) { "cell$it" })
        val elevations = FakeElevationRepository { points -> points.map { 1.0 } }

        EnrichCellElevationsUseCase(cells, elevations, { Coordinate(40.0, 49.0) })(limit = 100)

        assertEquals(100, cells.lastLimit)
        assertEquals(100, elevations.lastPoints.size)
    }
}
