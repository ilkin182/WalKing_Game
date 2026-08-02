package com.example.domain.usecase

import app.cash.turbine.test
import com.example.domain.model.ExploredCell
import com.example.domain.model.StompedHex
import com.example.domain.repository.StompedHexRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveExploredCellsUseCaseTest {
    private val repository: StompedHexRepository = mockk()

    @Test
    fun `maps stomped hexes to explored cells`() = runTest {
        every { repository.stompedHexes } returns flowOf(
            listOf(
                StompedHex("a", null, 1L),
                StompedHex("b", "Zone", 2L, explorationLevel = ExploredCell.LEVEL_VISION)
            )
        )

        ObserveExploredCellsUseCase(repository)().test {
            assertEquals(
                listOf(
                    ExploredCell("a", 1L, ExploredCell.LEVEL_WALKED),
                    ExploredCell("b", 2L, ExploredCell.LEVEL_VISION)
                ),
                awaitItem()
            )
            awaitComplete()
        }
    }

    @Test
    fun `cells default to fully explored so pre-fog history stays cleared`() = runTest {
        // Rows written before the fog existed carry no level; they were all recorded by walking
        // into them, so they have to come back as fully cleared, not as fog.
        every { repository.stompedHexes } returns flowOf(listOf(StompedHex("a", null, 1L)))

        ObserveExploredCellsUseCase(repository)().test {
            assertEquals(ExploredCell.LEVEL_WALKED, awaitItem().single().explorationLevel)
            awaitComplete()
        }
    }

    @Test
    fun `a Room query failure degrades to an empty list instead of crashing`() = runTest {
        every { repository.stompedHexes } returns flow { throw RuntimeException("SQLiteException") }

        ObserveExploredCellsUseCase(repository)().test {
            assertEquals(emptyList<ExploredCell>(), awaitItem())
            awaitComplete()
        }
    }
}
