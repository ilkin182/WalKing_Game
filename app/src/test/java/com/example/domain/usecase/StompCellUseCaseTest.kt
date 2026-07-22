package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.repository.StompedHexRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        coVerify { repository.stomp("cell_a", "Downtown") }
        coVerify { fillEnclosedAreas("cell_a", "Downtown", setOf("cell_a")) }
    }

    @Test
    fun `already-stomped cell is skipped unless forced`() = runTest {
        every { engine.latLngToCellAddress(1.0, 2.0, 11) } returns "cell_a"

        val result = useCase(1.0, 2.0, "Downtown", setOf("cell_a"))

        assertNull(result)
        coVerify(exactly = 0) { repository.stomp(any(), any()) }
        coVerify(exactly = 0) { fillEnclosedAreas(any(), any(), any()) }
    }

    @Test
    fun `forceRestomp re-stomps an already-claimed cell`() = runTest {
        every { engine.latLngToCellAddress(1.0, 2.0, 11) } returns "cell_a"

        val result = useCase(1.0, 2.0, "Downtown", setOf("cell_a"), forceRestomp = true)

        assertEquals("cell_a", result)
        coVerify { repository.stomp("cell_a", "Downtown") }
    }
}
