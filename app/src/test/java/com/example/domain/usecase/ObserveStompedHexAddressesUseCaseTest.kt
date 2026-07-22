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
import org.junit.Test

class ObserveStompedHexAddressesUseCaseTest {
    private val repository: StompedHexRepository = mockk()

    @Test
    fun `maps stomped hexes to a set of addresses`() = runTest {
        every { repository.stompedHexes } returns flowOf(
            listOf(StompedHex("a", null, 1L), StompedHex("b", "Zone", 2L))
        )
        val useCase = ObserveStompedHexAddressesUseCase(repository)

        useCase().test {
            assertEquals(setOf("a", "b"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a Room query failure degrades to an empty set instead of crashing`() = runTest {
        every { repository.stompedHexes } returns flow { throw RuntimeException("SQLiteException") }
        val useCase = ObserveStompedHexAddressesUseCase(repository)

        useCase().test {
            assertEquals(emptySet<String>(), awaitItem())
            awaitComplete()
        }
    }
}
