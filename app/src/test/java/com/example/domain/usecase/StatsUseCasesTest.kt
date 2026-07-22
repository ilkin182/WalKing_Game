package com.example.domain.usecase

import app.cash.turbine.test
import com.example.domain.repository.UserStatsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsUseCasesTest {
    private val repository: UserStatsRepository = mockk()

    @Test
    fun `ObserveNicknameUseCase delegates to the repository flow`() = runTest {
        every { repository.nickname } returns flowOf("Stomper")

        ObserveNicknameUseCase(repository)().test {
            assertEquals("Stomper", awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `ObserveTotalDistanceUseCase delegates to the repository flow`() = runTest {
        every { repository.totalDistanceWalked } returns flowOf(42.0)

        ObserveTotalDistanceUseCase(repository)().test {
            assertEquals(42.0, awaitItem(), 0.0)
            awaitComplete()
        }
    }

    @Test
    fun `ObserveStatsStartTimestampUseCase delegates to the repository flow`() = runTest {
        every { repository.statsStartTimestamp } returns flowOf(1234L)

        ObserveStatsStartTimestampUseCase(repository)().test {
            assertEquals(1234L, awaitItem())
            awaitComplete()
        }
    }
}
