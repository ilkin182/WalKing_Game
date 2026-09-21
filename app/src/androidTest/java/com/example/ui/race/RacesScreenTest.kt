package com.example.ui.race

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.navigation.createTestRaceUseCases
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test

/**
 * The races tab end to end against in-memory storage: set one up, look at it, hand somebody the
 * invitation, and come back the other way from a tapped link.
 */
class RacesScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: RaceViewModel

    private fun setContent() {
        viewModel = RaceViewModel(createTestRaceUseCases())
        composeTestRule.setContent {
            MyApplicationTheme {
                RacesScreen(viewModel = viewModel, onClose = {})
            }
        }
    }

    private fun createARace(name: String = "Həftəlik yarış") {
        composeTestRule.onNodeWithTag("create_race_button").performClick()
        composeTestRule.onNodeWithTag("race_name_field").performTextInput(name)
        composeTestRule.onNodeWithTag("confirm_create_race_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun opensOnTheEmptyStateWithBothWaysIn() {
        setContent()

        composeTestRule.onNodeWithTag("races_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_race_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("join_race_button").assertIsDisplayed()
    }

    @Test
    fun creatingARaceOpensItWithItsCodeAndInvitation() {
        setContent()

        createARace()

        // Straight into the race the host just made: the code and the share button are what they
        // came for.
        composeTestRule.onNodeWithTag("race_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("race_code_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_race_link_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("race_row_player").assertIsDisplayed()
    }

    @Test
    fun theCreatedRaceIsInTheListAfterGoingBack() {
        setContent()

        createARace()
        composeTestRule.onNodeWithTag("race_back_button").performClick()
        composeTestRule.waitForIdle()

        val code = viewModel.openRaceCodeForTest()
        composeTestRule.onNodeWithTag("race_card_$code").assertIsDisplayed()
    }

    @Test
    fun aTeamRaceShowsTheSharedGoalInsteadOfAPosition() {
        setContent()

        composeTestRule.onNodeWithTag("create_race_button").performClick()
        composeTestRule.onNodeWithTag("race_name_field").performTextInput("Birgə kəşf")
        composeTestRule.onNodeWithTag("race_format_COOP").performClick()
        composeTestRule.waitForIdle()

        // Picking the team format is what puts a goal on the form at all.
        composeTestRule.onNodeWithTag("race_target_field").assertIsDisplayed()

        composeTestRule.onNodeWithTag("confirm_create_race_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("team_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_total_text").assertIsDisplayed()
    }

    @Test
    fun aTappedInviteLinkOffersTheRaceBeforeJoiningIt() {
        setContent()

        viewModel.onInviteLink(
            "https://walkinggame.app/r/ZZZZ22?v=1&n=Dost+yar%C4%B1%C5%9F%C4%B1&m=CELLS&x=5" +
                "&s=1700000000000&e=9999999999999&h=Nigar&i=p-nigar"
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("race_invite_dialog").assertIsDisplayed()

        composeTestRule.onNodeWithTag("accept_invite_button").performClick()
        composeTestRule.waitForIdle()

        // Joined, and looking at the race rather than back at an empty list.
        composeTestRule.onNodeWithTag("race_summary_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("race_row_player").assertIsDisplayed()
    }

    @Test
    fun anUnreadableLinkSaysSoInsteadOfOpeningADialog() {
        setContent()

        viewModel.onInviteLink("https://example.com/not-an-invitation")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("race_message").assertIsDisplayed()
    }
}

/** The code of whichever race is open, for a test that needs to find its row in the list. */
private fun RaceViewModel.openRaceCodeForTest(): String =
    races.value.let { state ->
        (state as RacesUiState.Ready).boards.first().race.code
    }
