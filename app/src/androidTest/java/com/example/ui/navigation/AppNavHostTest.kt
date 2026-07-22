package com.example.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.domain.model.User
import com.example.domain.usecase.GetCurrentUserUseCase
import com.example.domain.usecase.LoginUseCase
import com.example.domain.usecase.LogoutUseCase
import com.example.domain.usecase.SendPasswordResetUseCase
import com.example.domain.usecase.SignUpUseCase
import com.example.ui.auth.AuthUseCases
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.FakeAuthRepository
import com.example.ui.map.GameViewModel
import com.example.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppNavHostTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var gameViewModel: GameViewModel

    @Before
    fun setUp() {
        // Granting these up front only affects what MapScreen renders (permission onboarding vs.
        // the map itself); it does not bypass or skip the login screen.
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant $packageName android.permission.ACCESS_FINE_LOCATION"
        )
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant $packageName android.permission.ACCESS_COARSE_LOCATION"
        )

        fakeAuthRepository = FakeAuthRepository()
        authViewModel = AuthViewModel(
            AuthUseCases(
                login = LoginUseCase(fakeAuthRepository),
                signUp = SignUpUseCase(fakeAuthRepository),
                logout = LogoutUseCase(fakeAuthRepository),
                getCurrentUser = GetCurrentUserUseCase(fakeAuthRepository),
                sendPasswordReset = SendPasswordResetUseCase(fakeAuthRepository)
            )
        )
        gameViewModel = GameViewModel(createTestGameUseCases())
    }

    @Test
    fun loginSuccess_navigatesToMapScreen() {
        fakeAuthRepository.loginResult = Result.success(User("uid1", "test@example.com"))

        composeTestRule.setContent {
            MyApplicationTheme {
                AppNavHost(authViewModel = authViewModel, gameViewModel = gameViewModel)
            }
        }

        composeTestRule.onNodeWithTag("login_email_field").performTextInput("test@example.com")
        composeTestRule.onNodeWithTag("login_password_field").performTextInput("password123")
        composeTestRule.onNodeWithTag("login_button").performClick()
        composeTestRule.waitForIdle()

        // Location permissions are pre-granted in setUp(), so the map route renders
        // GameMapContent directly (not the permission-onboarding screen); "recenter_button" is
        // one of its controls and doesn't exist on the login/signup screens.
        composeTestRule.onNodeWithTag("recenter_button").assertExists()
    }

    @Test
    fun logout_returnsToLoginScreen() {
        fakeAuthRepository.storedUser = User("uid1", "test@example.com")

        composeTestRule.setContent {
            MyApplicationTheme {
                AppNavHost(authViewModel = authViewModel, gameViewModel = gameViewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Already-logged-in session should start straight on the map, not the login screen.
        composeTestRule.onNodeWithTag("recenter_button").assertExists()

        composeTestRule.onNodeWithTag("profile_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("logout_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_email_field").assertExists()
    }
}
