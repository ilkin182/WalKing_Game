package com.example.domain.model

/**
 * One player's standing figures, as another player would see them.
 *
 * Deliberately only the two numbers the boards rank on plus who they belong to: everything else a
 * player has (their routes, where they walked, when) is theirs, and a leaderboard entry is the one
 * thing that is meant to leave the device once a backend exists.
 */
data class LeaderboardEntry(
    val playerId: String,
    val nickname: String,
    val countryCode: String,
    val exploredCells: Int,
    val unlockedAchievements: Int
)

/** What a board is sorted by. */
enum class LeaderboardCategory(val title: String, val shortLabel: String, val unitLabel: String) {
    CELLS("Kəşf edilmiş xanalar", "Xanalar", "xana"),
    ACHIEVEMENTS("Açılmış uğurlar", "Uğurlar", "nişan");

    fun scoreOf(entry: LeaderboardEntry): Int = when (this) {
        CELLS -> entry.exploredCells
        ACHIEVEMENTS -> entry.unlockedAchievements
    }
}

/** One row of a board: where a player placed, and the number they placed on. */
data class LeaderboardRank(
    val position: Int,
    val entry: LeaderboardEntry,
    val score: Int,
    val isCurrentPlayer: Boolean
)

/** A whole country's board for one category, already ordered and numbered. */
data class Leaderboard(
    val category: LeaderboardCategory,
    val countryCode: String,
    val rows: List<LeaderboardRank>
) {
    val playerRow: LeaderboardRank? get() = rows.firstOrNull { it.isCurrentPlayer }
    val playerCount: Int get() = rows.size
}
