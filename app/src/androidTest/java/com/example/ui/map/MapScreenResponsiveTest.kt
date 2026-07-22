package com.example.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.navigation.createTestGameUseCases
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.LocalWindowWidthSizeClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Verifies MapScreen picks the right profile-overlay layout for the width class, using
 * CompositionLocalProvider to force the value deterministically (physically resizing the
 * emulator mid-test proved to crash the instrumentation process — this avoids that entirely).
 */
class MapScreenResponsiveTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun grantLocationPermissions() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant $packageName android.permission.ACCESS_FINE_LOCATION"
        )
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "pm grant $packageName android.permission.ACCESS_COARSE_LOCATION"
        )
    }

    @Test
    fun compactWidth_usesTheFullScreenProfileOverlay() {
        val viewModel = GameViewModel(createTestGameUseCases())
        composeTestRule.setContent {
            CompositionLocalProvider(LocalWindowWidthSizeClass provides WindowWidthSizeClass.Compact) {
                MyApplicationTheme {
                    MapScreen(viewModel = viewModel, onLogout = {})
                }
            }
        }

        composeTestRule.onNodeWithTag("profile_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_fullscreen_overlay").assertExists()
    }

    @Test
    fun expandedWidth_usesTheSidePanelProfileOverlay() {
        val viewModel = GameViewModel(createTestGameUseCases())
        composeTestRule.setContent {
            CompositionLocalProvider(LocalWindowWidthSizeClass provides WindowWidthSizeClass.Expanded) {
                MyApplicationTheme {
                    MapScreen(viewModel = viewModel, onLogout = {})
                }
            }
        }

        composeTestRule.onNodeWithTag("profile_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_side_panel").assertExists()
    }
}
