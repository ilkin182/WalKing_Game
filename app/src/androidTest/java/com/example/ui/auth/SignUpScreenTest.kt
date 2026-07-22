package com.example.ui.auth

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

class SignUpScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel
    private var succeededUser: User? = null
    private var navigatedToLogin = false
    private var openedPrivacyPolicy = false

    @Before
    fun setUp() {
        fakeRepository = FakeAuthRepository()
        viewModel = AuthViewModel(
            AuthUseCases(
                login = LoginUseCase(fakeRepository),
                signUp = SignUpUseCase(fakeRepository),
                logout = LogoutUseCase(fakeRepository),
                getCurrentUser = GetCurrentUserUseCase(fakeRepository),
                sendPasswordReset = SendPasswordResetUseCase(fakeRepository)
            )
        )
        succeededUser = null
        navigatedToLogin = false
        openedPrivacyPolicy = false

        composeTestRule.setContent {
            MyApplicationTheme {
                SignUpScreen(
                    viewModel = viewModel,
                    onSignUpSuccess = { succeededUser = it },
                    onNavigateToLogin = { navigatedToLogin = true },
                    onOpenPrivacyPolicy = { openedPrivacyPolicy = true }
                )
            }
        }
    }

    @Test
    fun passwordMismatch_showsMismatchError() {
        composeTestRule.onNodeWithTag("signup_email_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("signup_password_field").performTextInput("password123")
        composeTestRule.onNodeWithTag("signup_confirm_password_field").performTextInput("password456")
        composeTestRule.onNodeWithTag("signup_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("signup_error_text")
            .assertTextEquals("Passwords do not match.")
    }

    @Test
    fun weakPassword_showsWeakPasswordError() {
        composeTestRule.onNodeWithTag("signup_email_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("signup_password_field").performTextInput("short")
        composeTestRule.onNodeWithTag("signup_confirm_password_field").performTextInput("short")
        composeTestRule.onNodeWithTag("signup_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("signup_error_text")
            .assertTextEquals("Password must be at least 8 characters.")
    }

    @Test
    fun successfulSignUp_invokesOnSignUpSuccess() {
        val user = User(uid = "uid2", email = "new@example.com")
        fakeRepository.signUpResult = Result.success(user)

        composeTestRule.onNodeWithTag("signup_email_field").performTextInput("new@example.com")
        composeTestRule.onNodeWithTag("signup_password_field").performTextInput("password123")
        composeTestRule.onNodeWithTag("signup_confirm_password_field").performTextInput("password123")
        composeTestRule.onNodeWithTag("signup_button").performClick()
        composeTestRule.waitForIdle()

        assert(succeededUser == user) { "Expected onSignUpSuccess to be called with $user, got $succeededUser" }
    }

    @Test
    fun loginLink_invokesOnNavigateToLogin() {
        composeTestRule.onNodeWithTag("signup_login_link").performClick()

        assert(navigatedToLogin) { "Expected onNavigateToLogin to be called" }
    }

    @Test
    fun privacyPolicyLink_invokesOnOpenPrivacyPolicy() {
        composeTestRule.onNodeWithTag("signup_privacy_policy_link").performClick()

        assert(openedPrivacyPolicy) { "Expected onOpenPrivacyPolicy to be called" }
    }
}
