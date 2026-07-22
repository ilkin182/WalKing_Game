package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetGridCellsAroundUseCaseTest {
    private val engine: HexGridEngine = mockk()
    private val useCase = GetGridCellsAroundUseCase(engine)

    @Test
    fun `marks cells present in the stomped set as stomped`() {
        every { engine.latLngToCellAddress(1.0, 2.0, 11) } returns "center"
        every { engine.gridDisk("center", 3) } returns listOf("center", "n1", "n2")
        every { engine.cellToBoundary(any()) } returns listOf(Coordinate(0.0, 0.0))

        val cells = useCase(1.0, 2.0, stompedAddresses = setOf("n1"), radiusSteps = 3)

        assertEquals(3, cells.size)
        assertTrue(cells.single { it.address == "n1" }.isStomped)
        assertTrue(cells.none { it.address == "center" && it.isStomped })
    }

    @Test
    fun `engine failure yields an empty list instead of throwing`() {
        every { engine.latLngToCellAddress(any(), any(), any()) } throws IllegalStateException("boom")

        val cells = useCase(1.0, 2.0, stompedAddresses = emptySet())

        assertTrue(cells.isEmpty())
    }
}
