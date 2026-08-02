package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The three destinations of the bottom bar, in the order they appear left to right.
 *
 * [MAP] sits in the middle because it is where the game is actually played - the two side tabs are
 * things a player checks and comes back from, so the one they return to is under their thumb either
 * way round.
 */
enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    ACHIEVEMENTS("Uğurlar", Icons.Default.EmojiEvents, "tab_achievements"),
    MAP("Xəritə", Icons.Default.Map, "tab_map"),
    PROFILE("Profil", Icons.Default.Person, "tab_profile");

    companion object {
        val DEFAULT = MAP
    }
}
