package com.example.ui.auth

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.example.domain.model.User
import com.example.domain.usecase.GetCurrentUserUseCase
import com.example.domain.usecase.LoginUseCase
import com.example.domain.usecase.LogoutUseCase
import com.example.domain.usecase.SendPasswordResetUseCase
import com.example.domain.usecase.SignUpUseCase
import com.example.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel
    private var succeededUser: User? = null
    private var navigatedToSignUp = false

    @Before
    fun setUp() {
        fakeRepository = FakeAuthRepository()
        viewModel = AuthViewModel(
            AuthUseCases(
                login = LoginUseCase(fakeRepository),
                signUp = SignUpUseCase(fakeRepository, FakeUserStatsRepository()),
                logout = LogoutUseCase(fakeRepository),
                getCurrentUser = GetCurrentUserUseCase(fakeRepository),
                sendPasswordReset = SendPasswordResetUseCase(fakeRepository)
            )
        )
        succeededUser = null
        navigatedToSignUp = false

        composeTestRule.setContent {
            MyApplicationTheme {
                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = { succeededUser = it },
                    onNavigateToSignUp = { navigatedToSignUp = true }
                )
            }
        }
    }

    @Test
    fun emptyFields_showsRequiredFieldsError() {
        composeTestRule.onNodeWithTag("login_button").performClick()

        composeTestRule.onNodeWithTag("login_error_text")
            .assertExists()
    }

    @Test
    fun invalidEmailFormat_showsValidationError() {
        composeTestRule.onNodeWithTag("login_email_field").performTextInput("not-an-email")
        composeTestRule.onNodeWithTag("login_password_field").performTextInput("password123")
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_error_text")
            .assertTextEquals("Please enter a valid email address.")
    }

    @Test
    fun wrongPassword_showsIncorrectCredentialsError() {
        fakeRepository.loginResult = Result.failure(AuthException(AuthFailure.InvalidCredentials))

        composeTestRule.onNodeWithTag("login_email_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("login_password_field").performTextInput("wrongpassword")
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_error_text")
            .assertTextEquals("Incorrect email or password.")
    }

    @Test
    fun validLogin_invokesOnLoginSuccess() {
        val user = User(uid = "uid1", email = "test@example.com")
        fakeRepository.loginResult = Result.success(user)

        composeTestRule.onNodeWithTag("login_email_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("login_password_field").performTextInput("password123")
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        assert(succeededUser == user) { "Expected onLoginSuccess to be called with $user, got $succeededUser" }
    }

    @Test
    fun signUpLink_invokesOnNavigateToSignUp() {
        composeTestRule.onNodeWithTag("login_signup_link").performClick()

        assert(navigatedToSignUp) { "Expected onNavigateToSignUp to be called" }
    }

    @Test
    fun forgotPassword_sendsResetEmailAndShowsConfirmation() {
        fakeRepository.sendPasswordResetResult = Result.success(Unit)

        composeTestRule.onNodeWithTag("login_forgot_password_link").performClick()
        composeTestRule.onNodeWithTag("forgot_password_email_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("forgot_password_send_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forgot_password_sent_message").assertExists()
    }

    @Test
    fun quickLoginDevButton_fillsCredentialsAndLogsIn() {
        // Debug builds compile with BuildConfig.DEBUG = true, so the dev-only button exists here;
        // it's release-only that's expected to R8-strip it (verified separately, not via UI test).
        val user = User(uid = "dev-uid", email = "test@example.com")
        fakeRepository.loginResult = Result.success(user)

        composeTestRule.onNodeWithTag("dev_quick_login_button").performClick()
        composeTestRule.waitForIdle()

        assert(succeededUser == user) { "Expected the quick-login button to log in and succeed" }
    }
}
