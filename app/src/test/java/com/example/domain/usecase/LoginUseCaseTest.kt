package com.example.domain.usecase

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginUseCaseTest {
    private val repository: AuthRepository = mockk()
    private lateinit var useCase: LoginUseCase

    @Before
    fun setUp() {
        useCase = LoginUseCase(repository)
    }

    @Test
    fun `valid credentials delegate to repository and return the user`() = runTest {
        val user = User(uid = "uid1", email = "test@example.com")
        coEvery { repository.login("test@example.com", "password123") } returns Result.success(user)

        val result = useCase("test@example.com", "password123")

        assertEquals(Result.success(user), result)
        coVerify(exactly = 1) { repository.login("test@example.com", "password123") }
    }

    @Test
    fun `invalid email format fails without calling repository`() = runTest {
        val result = useCase("not-an-email", "password123")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.InvalidEmailFormat, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.login(any(), any()) }
    }

    @Test
    fun `password shorter than 8 chars fails without calling repository`() = runTest {
        val result = useCase("test@example.com", "short")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.WeakPassword, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.login(any(), any()) }
    }

    @Test
    fun `repository failure is propagated`() = runTest {
        val failure = AuthException(AuthFailure.InvalidCredentials)
        coEvery { repository.login(any(), any()) } returns Result.failure(failure)

        val result = useCase("test@example.com", "password123")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.InvalidCredentials, (result.exceptionOrNull() as AuthException).failure)
    }

    @Test
    fun `email is trimmed before delegating to repository`() = runTest {
        coEvery { repository.login("test@example.com", "password123") } returns
            Result.success(User("uid1", "test@example.com"))

        useCase("  test@example.com  ", "password123")

        coVerify { repository.login("test@example.com", "password123") }
    }
}
