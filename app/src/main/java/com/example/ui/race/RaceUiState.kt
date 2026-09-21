package com.example.ui.race

import com.example.domain.model.Race
import com.example.domain.model.RaceBoard

/**
 * What the races tab can be showing.
 *
 * [Empty] is its own state rather than a [Ready] with no rows: a player who has never raced needs an
 * explanation of what a race is and two buttons, while a player whose races have all finished needs
 * a list. Those are different screens, not the same screen with a shorter list.
 */
sealed interface RacesUiState {
    data object Loading : RacesUiState
    data object Empty : RacesUiState
    data class Ready(val boards: List<RaceBoard>) : RacesUiState
}

/**
 * An invitation waiting to be accepted, from a tapped link or a typed code.
 *
 * [raw] is kept as well as [race] because it is what actually gets joined: the link carries the race
 * definition, and re-serialising a parsed copy would be a second chance to lose something from it.
 * [race] is null when the link arrived truncated - then all the screen can show is the code.
 */
data class PendingInvite(
    val raw: String,
    val code: String,
    val race: Race?
)

/**
 * Something the player needs told after an action finished.
 *
 * Deliberately a one-shot value the screen consumes rather than part of the list state: "bu yarış
 * doludur" is about the tap that just happened, and leaving it on screen while they look at
 * something else would make it look like a property of the race.
 */
data class RaceMessage(val text: String, val isError: Boolean = false)
