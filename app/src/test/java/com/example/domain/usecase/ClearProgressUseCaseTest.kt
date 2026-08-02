package com.example.domain.usecase

import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import com.example.domain.repository.WalkSessionRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClearProgressUseCaseTest {
    private val stompedHexRepository: StompedHexRepository = mockk(relaxed = true)
    private val userStatsRepository: UserStatsRepository = mockk(relaxed = true)
    private val walkSessionRepository: WalkSessionRepository = mockk(relaxed = true)
    private val useCase =
        ClearProgressUseCase(stompedHexRepository, userStatsRepository, walkSessionRepository)

    @Test
    fun `clears stomped hexes and resets stats`() = runTest {
        useCase()

        coVerify(exactly = 1) { stompedHexRepository.clearAll() }
        coVerify(exactly = 1) { userStatsRepository.resetStats() }
    }

    @Test
    fun `clears the walks and their routes along with the map`() = runTest {
        // Otherwise a reset map would still be judged against walks over ground that no longer
        // exists, and the route achievements would stay unlocked with nothing behind them.
        useCase()

        coVerify(exactly = 1) { walkSessionRepository.clearAll() }
    }
}
