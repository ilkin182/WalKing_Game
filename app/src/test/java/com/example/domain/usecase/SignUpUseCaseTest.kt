package com.example.domain.usecase

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.UserStatsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignUpUseCaseTest {
    private val repository: AuthRepository = mockk()
    private val profile: UserStatsRepository = mockk(relaxed = true)
    private lateinit var useCase: SignUpUseCase

    @Before
    fun setUp() {
        useCase = SignUpUseCase(repository, profile)
    }

    @Test
    fun `valid signup delegates to repository and records the country`() = runTest {
        val user = User(uid = "uid1", email = "new@example.com")
        coEvery { repository.signUp("new@example.com", "password123") } returns Result.success(user)

        val result = useCase("new@example.com", "password123", "password123", "AZ")

        assertEquals(user.copy(countryCode = "AZ"), result.getOrNull())
        verify(exactly = 1) { profile.updateCountry("AZ") }
    }

    @Test
    fun `country code is normalised before it is stored`() = runTest {
        val user = User(uid = "uid1", email = "new@example.com")
        coEvery { repository.signUp(any(), any()) } returns Result.success(user)

        val result = useCase("new@example.com", "password123", "password123", "az")

        assertEquals("AZ", result.getOrNull()?.countryCode)
        verify(exactly = 1) { profile.updateCountry("AZ") }
    }

    @Test
    fun `invalid email fails without calling repository`() = runTest {
        val result = useCase("not-an-email", "password123", "password123", "AZ")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.InvalidEmailFormat, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
    }

    @Test
    fun `weak password fails without calling repository`() = runTest {
        val result = useCase("new@example.com", "short", "short", "AZ")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.WeakPassword, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
    }

    @Test
    fun `mismatched confirm password fails without calling repository`() = runTest {
        val result = useCase("new@example.com", "password123", "password456", "AZ")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.PasswordsDoNotMatch, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
    }

    @Test
    fun `an unknown country fails without calling repository`() = runTest {
        val result = useCase("new@example.com", "password123", "password123", "ZZ")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.CountryNotSelected, (result.exceptionOrNull() as AuthException).failure)
        coVerify(exactly = 0) { repository.signUp(any(), any()) }
        verify(exactly = 0) { profile.updateCountry(any()) }
    }

    @Test
    fun `repository collision failure is propagated and leaves no country behind`() = runTest {
        coEvery { repository.signUp(any(), any()) } returns
            Result.failure(AuthException(AuthFailure.EmailAlreadyInUse))

        val result = useCase("new@example.com", "password123", "password123", "AZ")

        assertTrue(result.isFailure)
        assertEquals(AuthFailure.EmailAlreadyInUse, (result.exceptionOrNull() as AuthException).failure)
        verify(exactly = 0) { profile.updateCountry(any()) }
    }
}
