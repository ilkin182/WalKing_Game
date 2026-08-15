package com.example.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ui.map.GameViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.LocalWindowWidthSizeClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The bottom bar's contract: four tabs, the map open by default, and the three side tabs presented
 * as full-screen sheets on phones but side panels on tablets (the width class is forced through
 * CompositionLocalProvider - physically resizing the emulator mid-test crashed the instrumentation
 * process).
 */
class MainShellTest {
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

    private fun setContent(widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        val viewModel = GameViewModel(createTestGameUseCases())
        composeTestRule.setContent {
            CompositionLocalProvider(LocalWindowWidthSizeClass provides widthSizeClass) {
                MyApplicationTheme {
                    MainShell(viewModel = viewModel, onLogout = {})
                }
            }
        }
    }

    @Test
    fun startsOnTheMapTabWithEveryTabAvailable() {
        setContent()

        composeTestRule.onNodeWithTag("tab_map").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_achievements").assertExists()
        composeTestRule.onNodeWithTag("tab_leaderboard").assertExists()
        composeTestRule.onNodeWithTag("tab_profile").assertExists()
        // The map's own controls are reachable, so the bar is not covering them.
        composeTestRule.onNodeWithTag("recenter_button").assertExists()
    }

    @Test
    fun achievementsTabOpensTheAchievementsScreen() {
        setContent()

        composeTestRule.onNodeWithTag("tab_achievements").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("achievements_overlay_fullscreen").assertExists()
        composeTestRule.onNodeWithTag("achievements_screen_container").assertExists()
    }

    @Test
    fun profileTabOpensTheProfileScreen() {
        setContent()

        composeTestRule.onNodeWithTag("tab_profile").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_overlay_fullscreen").assertExists()
        composeTestRule.onNodeWithTag("profile_screen_container").assertExists()
    }

    @Test
    fun leaderboardTabOpensTheCountryRanking() {
        setContent()

        composeTestRule.onNodeWithTag("tab_leaderboard").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("leaderboard_overlay_fullscreen").assertExists()
        composeTestRule.onNodeWithTag("leaderboard_screen_container").assertExists()
        // The fake player is in a country, so the board itself renders rather than the picker prompt.
        composeTestRule.onNodeWithTag("player_standing_card").assertExists()
    }

    @Test
    fun closingATabReturnsToTheMap() {
        setContent()

        composeTestRule.onNodeWithTag("tab_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("close_profile_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_map").assertIsSelected()
    }

    @Test
    fun expandedWidthUsesSidePanelsSoTheMapStaysVisible() {
        setContent(WindowWidthSizeClass.Expanded)

        composeTestRule.onNodeWithTag("tab_profile").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_overlay_side_panel").assertExists()
    }
}
