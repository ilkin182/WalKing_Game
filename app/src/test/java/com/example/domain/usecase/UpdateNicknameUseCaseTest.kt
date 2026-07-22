package com.example.domain.usecase

import com.example.domain.repository.UserStatsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class UpdateNicknameUseCaseTest {
    private val repository: UserStatsRepository = mockk(relaxed = true)
    private val useCase = UpdateNicknameUseCase(repository)

    @Test
    fun `trims and saves a valid nickname`() {
        useCase("  Stomper  ")

        verify(exactly = 1) { repository.updateNickname("Stomper") }
    }

    @Test
    fun `blank nickname is not saved`() {
        useCase("   ")

        verify(exactly = 0) { repository.updateNickname(any()) }
    }
}
