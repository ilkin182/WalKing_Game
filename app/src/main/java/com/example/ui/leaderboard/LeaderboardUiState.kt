package com.example.ui.leaderboard

import com.example.domain.model.Leaderboard

/**
 * What the ranking tab can be showing.
 *
 * [CountryMissing] is its own state rather than an empty board: an account made before sign-up asked
 * for a country has nothing wrong with it, it just has nowhere to be ranked yet, and the screen
 * answers that with a picker instead of an error.
 */
sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data object CountryMissing : LeaderboardUiState
    data class Ready(val board: Leaderboard) : LeaderboardUiState
}
