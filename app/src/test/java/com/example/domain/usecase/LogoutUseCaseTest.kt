package com.example.domain.usecase

import com.example.domain.repository.AuthRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class LogoutUseCaseTest {
    private val repository: AuthRepository = mockk(relaxed = true)
    private val useCase = LogoutUseCase(repository)

    @Test
    fun `invoking logs out through the repository exactly once`() {
        useCase()

        verify(exactly = 1) { repository.logout() }
    }
}
