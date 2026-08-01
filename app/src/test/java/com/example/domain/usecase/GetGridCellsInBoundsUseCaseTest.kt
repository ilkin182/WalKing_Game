package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetGridCellsInBoundsUseCaseTest {
    private val engine: HexGridEngine = mockk()
    private val useCase = GetGridCellsInBoundsUseCase(engine)

    private val bounds = listOf(
        Coordinate(1.0, 2.0),
        Coordinate(1.0, 3.0),
        Coordinate(0.0, 3.0),
        Coordinate(0.0, 2.0)
    )

    @Test
    fun `marks cells present in the stomped set as stomped`() {
        every { engine.polygonToCells(bounds, 11) } returns setOf("a", "n1", "n2")
        every { engine.cellToBoundary(any()) } returns listOf(Coordinate(0.0, 0.0))

        val cells = useCase(bounds, stompedAddresses = setOf("n1"))

        assertEquals(3, cells.size)
        assertTrue(cells.single { it.address == "n1" }.isStomped)
        assertTrue(cells.none { it.address == "a" && it.isStomped })
    }

    @Test
    fun `engine failure yields an empty list instead of throwing`() {
        every { engine.polygonToCells(any(), any()) } throws IllegalStateException("boom")

        val cells = useCase(bounds, stompedAddresses = emptySet())

        assertTrue(cells.isEmpty())
    }

    @Test
    fun `tiles the whole requested area when it sits inside the coverage radius`() {
        // ~1 km north-east of the coverage center, well within the 100 km radius.
        val nearby = listOf(
            Coordinate(40.4100, 49.8600),
            Coordinate(40.4100, 49.8700),
            Coordinate(40.4000, 49.8700),
            Coordinate(40.4000, 49.8600)
        )
        val requested = slot<List<Coordinate>>()
        every { engine.polygonToCells(capture(requested), 11) } returns setOf("a")
        every { engine.cellToBoundary(any()) } returns listOf(Coordinate(0.0, 0.0))

        val cells = useCase(nearby, emptySet(), coverageCenter = Coordinate(40.4093, 49.8671))

        assertEquals(1, cells.size)
        assertEquals(40.4100, requested.captured.maxOf { it.lat }, 1e-9)
        assertEquals(40.4000, requested.captured.minOf { it.lat }, 1e-9)
        assertEquals(49.8700, requested.captured.maxOf { it.lng }, 1e-9)
        assertEquals(49.8600, requested.captured.minOf { it.lng }, 1e-9)
    }

    @Test
    fun `clips an area that only partly overlaps the coverage radius`() {
        // Spans from the coverage center out to ~330 km east; only the first ~100 km is covered.
        val overshooting = listOf(
            Coordinate(40.4200, 49.8671),
            Coordinate(40.4200, 53.8671),
            Coordinate(40.4000, 53.8671),
            Coordinate(40.4000, 49.8671)
        )
        val requested = slot<List<Coordinate>>()
        every { engine.polygonToCells(capture(requested), 11) } returns setOf("a")
        every { engine.cellToBoundary(any()) } returns listOf(Coordinate(0.0, 0.0))

        useCase(overshooting, emptySet(), coverageCenter = Coordinate(40.4093, 49.8671))

        val clippedEast = requested.captured.maxOf { it.lng }
        assertTrue("east edge should be pulled back inside the radius", clippedEast < 53.8671)
        // 100 km of longitude at this latitude is ~1.18 degrees.
        assertEquals(49.8671 + 1.183, clippedEast, 0.01)
        assertEquals(49.8671, requested.captured.minOf { it.lng }, 1e-9)
    }

    @Test
    fun `skips tiling entirely for an area beyond the coverage radius`() {
        val faraway = listOf(
            Coordinate(48.8600, 2.3500),
            Coordinate(48.8600, 2.3600),
            Coordinate(48.8500, 2.3600),
            Coordinate(48.8500, 2.3500)
        )

        val cells = useCase(faraway, emptySet(), coverageCenter = Coordinate(40.4093, 49.8671))

        assertTrue(cells.isEmpty())
        verify(exactly = 0) { engine.polygonToCells(any(), any()) }
    }
}
