package com.example.ui.auth

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.usecase.GetCurrentUserUseCase
import com.example.domain.usecase.LoginUseCase
import com.example.domain.usecase.LogoutUseCase
import com.example.domain.usecase.SendPasswordResetUseCase
import com.example.domain.usecase.SignUpUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val login: LoginUseCase = mockk()
    private val signUp: SignUpUseCase = mockk()
    private val logout: LogoutUseCase = mockk(relaxed = true)
    private val getCurrentUser: GetCurrentUserUseCase = mockk()
    private val sendPasswordReset: SendPasswordResetUseCase = mockk()

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = AuthViewModel(
            AuthUseCases(login, signUp, logout, getCurrentUser, sendPasswordReset)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login success transitions Idle to Loading to Success`() = runTest {
        val user = User("uid1", "test@example.com")
        coEvery { login("test@example.com", "pw123456") } returns Result.success(user)

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)

        viewModel.login("test@example.com", "pw123456")
        assertEquals(AuthUiState.Loading, viewModel.uiState.value)

        testScheduler.advanceUntilIdle()
        assertEquals(AuthUiState.Success(user), viewModel.uiState.value)
    }

    @Test
    fun `login failure transitions to Error with a friendly message`() = runTest {
        coEvery { login(any(), any()) } returns Result.failure(AuthException(AuthFailure.InvalidCredentials))

        viewModel.login("test@example.com", "wrongpass")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Incorrect email or password.", (state as AuthUiState.Error).message)
    }

    @Test
    fun `signUp success transitions to Success`() = runTest {
        val user = User("uid2", "new@example.com")
        coEvery { signUp("new@example.com", "pw123456", "pw123456") } returns Result.success(user)

        viewModel.signUp("new@example.com", "pw123456", "pw123456")
        testScheduler.advanceUntilIdle()

        assertEquals(AuthUiState.Success(user), viewModel.uiState.value)
    }

    @Test
    fun `signUp failure surfaces the mapped error message`() = runTest {
        coEvery { signUp(any(), any(), any()) } returns Result.failure(AuthException(AuthFailure.EmailAlreadyInUse))

        viewModel.signUp("new@example.com", "pw123456", "pw123456")
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AuthUiState.Error
        assertEquals("An account with this email already exists.", state.message)
    }

    @Test
    fun `sendPasswordReset success flips the sent flag and returns to Idle`() = runTest {
        coEvery { sendPasswordReset("test@example.com") } returns Result.success(Unit)

        viewModel.sendPasswordReset("test@example.com")
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.passwordResetEmailSent.value)
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `sendPasswordReset failure does not flip the sent flag`() = runTest {
        coEvery { sendPasswordReset(any()) } returns Result.failure(AuthException(AuthFailure.InvalidEmailFormat))

        viewModel.sendPasswordReset("bad-email")
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.passwordResetEmailSent.value)
        assertTrue(viewModel.uiState.value is AuthUiState.Error)
    }

    @Test
    fun `logout invokes the use case and resets state to Idle`() {
        viewModel.logout()

        verify(exactly = 1) { logout() }
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `currentUser delegates to GetCurrentUserUseCase`() {
        val user = User("uid1", "test@example.com")
        every { getCurrentUser() } returns user

        assertEquals(user, viewModel.currentUser())
    }
}
