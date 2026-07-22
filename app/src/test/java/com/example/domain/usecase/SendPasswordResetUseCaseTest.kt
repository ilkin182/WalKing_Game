package com.example.domain.usecase

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SendPasswordResetUseCaseTest {
    private val repository: AuthRepository = mockk()
    private lateinit var useCase: SendPasswordResetUseCase

    @Before
    fun setUp() {
        useCase = SendPasswordResetUseCase(repository)
    }

    @Test
    fun `valid email delegates to repository`() = runTest {
        coEvery { repository.sendPasswordResetEmail("test@example.com") } returns Result.success(Unit)

        val result = useCase("test@example.com")

        assertTrue(result.isSuccess)
        coVerify { repository.sendPasswordResetEmail("test@example.com") }
    }

    @Test
    fun `invalid email fails without calling repository`() = runTest {
        val result = useCase("not-an-email")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.InvalidEmailFormat, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.sendPasswordResetEmail(any()) }
    }

    @Test
    fun `repository network failure is propagated`() = runTest {
        coEvery { repository.sendPasswordResetEmail(any()) } returns
            Result.failure(AuthException(AuthFailure.NetworkError))

        val result = useCase("test@example.com")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.NetworkError, (result.exceptionOrNull() as AuthException).failure)
    }
}
