package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import io.mockk.every
import io.mockk.mockk
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
}
