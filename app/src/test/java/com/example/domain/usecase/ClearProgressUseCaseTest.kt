package com.example.domain.usecase

import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearProgressUseCaseTest {
    private val stompedHexRepository: StompedHexRepository = mockk(relaxed = true)
    private val userStatsRepository: UserStatsRepository = mockk(relaxed = true)
    private val useCase = ClearProgressUseCase(stompedHexRepository, userStatsRepository)

    @Test
    fun `clears stomped hexes and resets stats`() = runTest {
        useCase()

        coVerify(exactly = 1) { stompedHexRepository.clearAll() }
        coVerify(exactly = 1) { userStatsRepository.resetStats() }
    }
}
