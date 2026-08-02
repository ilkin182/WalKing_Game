package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import com.example.domain.repository.StompedHexRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Adjacency-map hex engine so the flood-fill graph is fully controlled and deterministic. */
private class FakeHexGridEngine(private val adjacency: Map<String, List<String>>) : HexGridEngine {
    override fun latLngToCellAddress(lat: Double, lng: Double, resolution: Int) = error("not used")
    override fun cellToBoundary(cellAddress: String): List<Coordinate> = error("not used")
    override fun gridDisk(centerCellAddress: String, radius: Int): List<String> =
        adjacency[centerCellAddress].orEmpty()
    override fun polygonToCells(corners: List<Coordinate>, resolution: Int) = error("not used")
}

class FillEnclosedAreasUseCaseTest {
    private val repository: StompedHexRepository = mockk(relaxed = true)

    @Test
    fun `a pocket fully surrounded by stomped cells is auto-filled`() = runTest {
        // "center" only borders the 6 ring cells; each ring cell only borders "center".
        val ring = listOf("n1", "n2", "n3", "n4", "n5", "n6")
        val adjacency = mapOf("center" to ring) + ring.associateWith { listOf("center") }
        val useCase = FillEnclosedAreasUseCase(repository, FakeHexGridEngine(adjacency))

        // n6 is the cell that just completed the ring; the other 5 were already stomped.
        val loops = useCase("n6", "Downtown", stompedAddresses = setOf("n1", "n2", "n3", "n4", "n5"))

        assertEquals(1, loops)
        coVerify { repository.stompAll(match { it.toSet() == setOf("center") }, "Downtown") }
    }

    @Test
    fun `closing a loop is reported so it can be counted`() = runTest {
        // The event leaves no trace once the interior is claimed - it looks exactly like ground the
        // player walked over - so it has to be reported as it happens or not at all.
        val ring = listOf("n1", "n2", "n3", "n4", "n5", "n6")
        val adjacency = mapOf("center" to ring) + ring.associateWith { listOf("center") }
        val reported = mutableListOf<Int>()
        val useCase = FillEnclosedAreasUseCase(
            repository = repository,
            hexGridEngine = FakeHexGridEngine(adjacency),
            onAreasEnclosed = { reported.add(it) }
        )

        useCase("n6", "Downtown", stompedAddresses = setOf("n1", "n2", "n3", "n4", "n5"))

        assertEquals(listOf(1), reported)
    }

    @Test
    fun `filling nothing reports nothing`() = runTest {
        val adjacency = mapOf("center" to listOf("n1"), "n1" to listOf("center"))
        val reported = mutableListOf<Int>()
        val useCase = FillEnclosedAreasUseCase(
            repository = repository,
            hexGridEngine = FakeHexGridEngine(adjacency),
            onAreasEnclosed = { reported.add(it) }
        )

        // "n1" opens onto nothing else in this graph, so it is enclosed; but with the neighbour
        // already stomped there is no pocket left to fill.
        val loops = useCase("center", "Downtown", stompedAddresses = setOf("n1"))

        assertEquals(0, loops)
        assertEquals(emptyList<Int>(), reported)
    }

    @Test
    fun `an unbounded region is never filled`() = runTest {
        // A long open chain: center -> n1 -> n2 -> ... -> n250, never closing back on itself.
        val chain = (1..250).map { "n$it" }
        val adjacency = mutableMapOf<String, List<String>>()
        adjacency["center"] = listOf(chain.first())
        for (i in chain.indices) {
            val neighbors = mutableListOf<String>()
            if (i == 0) neighbors.add("center") else neighbors.add(chain[i - 1])
            if (i < chain.lastIndex) neighbors.add(chain[i + 1])
            adjacency[chain[i]] = neighbors
        }
        val useCase = FillEnclosedAreasUseCase(repository, FakeHexGridEngine(adjacency))

        val loops = useCase("center", "Downtown", stompedAddresses = emptySet())

        assertEquals(0, loops)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
    }
}
