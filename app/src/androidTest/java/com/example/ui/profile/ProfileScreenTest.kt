package com.example.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.ui.map.GameViewModel
import com.example.ui.navigation.createTestGameUseCases
import com.example.ui.theme.MyApplicationTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class ProfileScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** The screen titles the metrics are shown under; uppercasing "i" is locale-sensitive here. */
    private val azLocale: Locale = Locale.Builder().setLanguage("az").build()

    private lateinit var viewModel: GameViewModel
    private var closed = false
    private var loggedOut = false
    private var openedPrivacyPolicy = false

    @Before
    fun setUp() {
        viewModel = GameViewModel(createTestGameUseCases())
        closed = false
        loggedOut = false
        openedPrivacyPolicy = false

        composeTestRule.setContent {
            MyApplicationTheme {
                ProfileScreen(
                    viewModel = viewModel,
                    onClose = { closed = true },
                    onLogout = { loggedOut = true },
                    onOpenPrivacyPolicy = { openedPrivacyPolicy = true }
                )
            }
        }
    }

    @Test
    fun closeButton_invokesOnClose() {
        composeTestRule.onNodeWithTag("close_profile_button").performClick()

        assert(closed) { "Expected onClose to be called" }
    }

    @Test
    fun logoutButton_invokesOnLogout() {
        composeTestRule.onNodeWithTag("logout_button").performClick()

        assert(loggedOut) { "Expected onLogout to be called" }
    }

    @Test
    fun privacyPolicyButton_invokesOnOpenPrivacyPolicy() {
        composeTestRule.onNodeWithTag("open_privacy_policy_button").performClick()

        assert(openedPrivacyPolicy) { "Expected onOpenPrivacyPolicy to be called" }
    }

    @Test
    fun editingNickname_andSaving_updatesTheDisplayedName() {
        composeTestRule.onNodeWithTag("edit_nickname_trigger").performClick()
        composeTestRule.onNodeWithTag("nickname_input_field").performTextClearance()
        composeTestRule.onNodeWithTag("nickname_input_field").performTextInput("NewName")
        composeTestRule.onNodeWithTag("save_nickname_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("NewName").assertExists()
    }

    @Test
    fun tappingAStatCard_opensItsWeeklyBreakdown() {
        openBreakdown("distance_stat_card")

        composeTestRule.onNodeWithTag("weekly_stats_screen").assertExists()
        composeTestRule.onNodeWithText("Son 7 gün").assertExists()
        composeTestRule.onNodeWithText("HƏFTƏLİK CƏM").assertExists()
    }

    /** All three headline cards drill in, each to its own metric. */
    @Test
    fun eachStatCard_opensItsOwnBreakdown() {
        val titles = mapOf(
            "distance_stat_card" to StatMetric.DISTANCE,
            "area_stat_card" to StatMetric.AREA,
            "cells_stat_card" to StatMetric.CELLS
        )

        titles.forEach { (tag, metric) ->
            openBreakdown(tag)
            composeTestRule.onNodeWithText(metric.title.uppercase(azLocale)).assertExists()

            composeTestRule.onNodeWithTag("weekly_stats_back_button").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun weeklyBreakdown_backButton_closesIt() {
        openBreakdown("cells_stat_card")

        composeTestRule.onNodeWithTag("weekly_stats_back_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("weekly_stats_screen").assertDoesNotExist()
    }

    private fun openBreakdown(cardTag: String) {
        composeTestRule.onNodeWithTag(cardTag).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun editingNickname_andCancelling_leavesTheNameUnchanged() {
        composeTestRule.onNodeWithTag("edit_nickname_trigger").performClick()
        composeTestRule.onNodeWithTag("nickname_input_field").performTextClearance()
        composeTestRule.onNodeWithTag("nickname_input_field").performTextInput("Discarded")
        composeTestRule.onNodeWithTag("cancel_nickname_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Discarded").assertDoesNotExist()
    }
}
