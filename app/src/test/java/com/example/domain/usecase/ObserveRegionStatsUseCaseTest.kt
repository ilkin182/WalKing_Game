package com.example.domain.usecase

import app.cash.turbine.test
import com.example.domain.model.StompedHex
import com.example.domain.repository.StompedHexRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveRegionStatsUseCaseTest {
    private val repository: StompedHexRepository = mockk()

    @Test
    fun `groups hexes by neighborhood, excludes Active Zone, sorted by percentage desc`() = runTest {
        val hexes = listOf(
            StompedHex("h1", "Downtown", 1L),
            StompedHex("h2", "Downtown", 2L),
            StompedHex("h3", "Uptown", 3L),
            StompedHex("h4", "Active Zone", 4L),
            StompedHex("h5", null, 5L)
        )
        every { repository.stompedHexes } returns flowOf(hexes)
        val useCase = ObserveRegionStatsUseCase(repository)

        useCase().test {
            val stats = awaitItem()
            assertEquals(setOf("Downtown", "Uptown"), stats.map { it.name }.toSet())
            assertTrue(stats.none { it.name == "Active Zone" })
            assertEquals(2, stats.single { it.name == "Downtown" }.exploredHexes)
            assertEquals(1, stats.single { it.name == "Uptown" }.exploredHexes)
            // sorted descending by percentage
            assertTrue(stats.zipWithNext().all { (a, b) -> a.percentage >= b.percentage })
            awaitComplete()
        }
    }

    @Test
    fun `empty repository yields empty stats`() = runTest {
        every { repository.stompedHexes } returns flowOf(emptyList())
        val useCase = ObserveRegionStatsUseCase(repository)

        useCase().test {
            assertEquals(emptyList<Any>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a Room query failure degrades to an empty list instead of crashing`() = runTest {
        every { repository.stompedHexes } returns flow { throw RuntimeException("SQLiteException") }
        val useCase = ObserveRegionStatsUseCase(repository)

        useCase().test {
            assertEquals(emptyList<Any>(), awaitItem())
            awaitComplete()
        }
    }
}
