package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import com.example.domain.repository.StompedHexRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StompCellUseCaseTest {
    private val repository: StompedHexRepository = mockk(relaxed = true)
    private val engine: HexGridEngine = mockk()
    private val fillEnclosedAreas: FillEnclosedAreasUseCase = mockk(relaxed = true)
    private lateinit var useCase: StompCellUseCase

    @Before
    fun setUp() {
        useCase = StompCellUseCase(repository, engine, fillEnclosedAreas)
    }

    @Test
    fun `stomps a new cell and triggers enclosed-area fill`() = runTest {
        every { engine.latLngToCellAddress(1.0, 2.0, 11) } returns "cell_a"

        val result = useCase(1.0, 2.0, "Downtown", emptySet())

        assertEquals("cell_a", result)
        coVerify { repository.stompAll(listOf("cell_a"), "Downtown") }
        coVerify { fillEnclosedAreas("cell_a", "Downtown", setOf("cell_a")) }
    }

    @Test
    fun `already-stomped cell is skipped unless forced`() = runTest {
        every { engine.latLngToCellAddress(1.0, 2.0, 11) } returns "cell_a"

        val result = useCase(1.0, 2.0, "Downtown", setOf("cell_a"))

        assertNull(result)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
        coVerify(exactly = 0) { fillEnclosedAreas(any(), any(), any()) }
    }

    @Test
    fun `forceRestomp re-stomps an already-claimed cell`() = runTest {
        every { engine.latLngToCellAddress(1.0, 2.0, 11) } returns "cell_a"

        val result = useCase(1.0, 2.0, "Downtown", setOf("cell_a"), forceRestomp = true)

        assertEquals("cell_a", result)
        coVerify { repository.stompAll(listOf("cell_a"), "Downtown") }
    }

    @Test
    fun `movement since the previous fix claims the cells in between`() = runTest {
        stubBandGrid()
        // 100 m north of the previous fix - two bands further on, so stomping only the two fix
        // positions would leave the band between them unclaimed.
        val result = useCase(originLat + 0.0009, originLng, "Downtown", emptySet(), from = origin)

        assertEquals("cell_2", result)
        assertEquals(listOf("cell_0", "cell_1", "cell_2"), capturedStomps())
    }

    @Test
    fun `trail cells are claimed even when the destination cell already is`() = runTest {
        stubBandGrid()

        val result = useCase(originLat + 0.0005, originLng, "Downtown", setOf("cell_1"), from = origin)

        assertEquals("cell_1", result)
        assertEquals(listOf("cell_0"), capturedStomps())
    }

    @Test
    fun `a GPS jump too long to have been walked claims no trail`() = runTest {
        stubBandGrid()
        // 1.1 km from the previous fix: a provider glitch, not movement - only where the player
        // now is gets claimed, not the corridor they never walked.
        val jumpedTo = originLat + 0.01
        useCase(jumpedTo, originLng, "Downtown", emptySet(), from = origin)

        assertEquals(listOf(bandAt(jumpedTo)), capturedStomps())
    }

    @Test
    fun `a stationary player claims nothing new`() = runTest {
        stubBandGrid()

        val result = useCase(originLat, originLng, "Downtown", setOf("cell_0"), from = origin)

        assertNull(result)
        coVerify(exactly = 0) { repository.stompAll(any(), any()) }
    }

    @Test
    fun `enclosed-area fill sees the whole trail as stomped`() = runTest {
        stubBandGrid()

        useCase(originLat + 0.0005, originLng, "Downtown", setOf("old"), from = origin)

        val known = slot<Set<String>>()
        coVerify { fillEnclosedAreas("cell_1", "Downtown", capture(known)) }
        assertTrue(known.captured.containsAll(setOf("old", "cell_0", "cell_1")))
    }

    private fun capturedStomps(): List<String> {
        val stomped = slot<List<String>>()
        coVerify { repository.stompAll(capture(stomped), any()) }
        return stomped.captured
    }

    /**
     * Stands in for the real grid with latitude bands roughly as wide as a cell at resolution 11,
     * so a segment covered between two GPS fixes spans more than one of them.
     */
    private fun stubBandGrid() {
        every { engine.latLngToCellAddress(any(), any(), 11) } answers { bandAt(firstArg()) }
    }

    private fun bandAt(lat: Double) =
        "cell_${Math.floor((lat - originLat) / BAND_HEIGHT_DEGREES).toInt()}"

    private companion object {
        const val originLat = 40.4093
        const val originLng = 49.8671
        val origin = Coordinate(originLat, originLng)

        // ~44 m, close to the width of a cell at resolution 11.
        const val BAND_HEIGHT_DEGREES = 0.0004
    }
}
