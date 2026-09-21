package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The five destinations of the bottom bar, in the order they appear left to right.
 *
 * [MAP] sits left of centre rather than at an end: it is where the game is actually played, and the
 * others are things a player checks and comes back from, so the one they return to stays near the
 * middle of the bar whichever hand is holding the phone.
 *
 * [RACES] follows the map because it is the most time-sensitive thing on the bar - a race has a
 * deadline and other people in it, so "did that walk move me up?" is asked there first and on the
 * country board ([LEADERBOARD]) second. The profile is the settings-shaped tab and stays at the far
 * end.
 */
enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    ACHIEVEMENTS("Uğurlar", Icons.Default.EmojiEvents, "tab_achievements"),
    MAP("Xəritə", Icons.Default.Map, "tab_map"),
    RACES("Yarış", Icons.Default.Flag, "tab_races"),
    LEADERBOARD("Reytinq", Icons.Default.Leaderboard, "tab_leaderboard"),
    PROFILE("Profil", Icons.Default.Person, "tab_profile");

    companion object {
        val DEFAULT = MAP
    }
}
