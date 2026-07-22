package com.example.domain.usecase

import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetCurrentUserUseCaseTest {
    private val repository: AuthRepository = mockk()
    private val useCase = GetCurrentUserUseCase(repository)

    @Test
    fun `returns the current user when a session exists`() {
        val user = User(uid = "uid1", email = "test@example.com")
        every { repository.getCurrentUser() } returns user

        assertEquals(user, useCase())
    }

    @Test
    fun `returns null when there is no persisted session`() {
        every { repository.getCurrentUser() } returns null

        assertNull(useCase())
    }
}
