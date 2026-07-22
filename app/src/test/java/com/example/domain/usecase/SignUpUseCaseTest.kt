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

class SignUpUseCaseTest {
    private val repository: AuthRepository = mockk()
    private lateinit var useCase: SignUpUseCase

    @Before
    fun setUp() {
        useCase = SignUpUseCase(repository)
    }

    @Test
    fun `valid signup delegates to repository`() = runTest {
        val user = User(uid = "uid1", email = "new@example.com")
        coEvery { repository.signUp("new@example.com", "password123") } returns Result.success(user)

        val result = useCase("new@example.com", "password123", "password123")

        assertEquals(Result.success(user), result)
    }

    @Test
    fun `invalid email fails without calling repository`() = runTest {
        val result = useCase("not-an-email", "password123", "password123")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.InvalidEmailFormat, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
    }

    @Test
    fun `weak password fails without calling repository`() = runTest {
        val result = useCase("new@example.com", "short", "short")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.WeakPassword, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
    }

    @Test
    fun `mismatched confirm password fails without calling repository`() = runTest {
        val result = useCase("new@example.com", "password123", "password456")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.PasswordsDoNotMatch, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
    }

    @Test
    fun `repository collision failure is propagated`() = runTest {
        coEvery { repository.signUp(any(), any()) } returns
            Result.failure(AuthException(AuthFailure.EmailAlreadyInUse))

        val result = useCase("new@example.com", "password123", "password123")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.EmailAlreadyInUse, (result.exceptionOrNull() as AuthException).failure)
    }
}
