package com.example.domain.usecase

import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository

class ClearProgressUseCase(
    private val stompedHexRepository: StompedHexRepository,
    private val userStatsRepository: UserStatsRepository
) {
    suspend operator fun invoke() {
        stompedHexRepository.clearAll()
        userStatsRepository.resetStats()
    }
}
