package com.example.domain.leaderboard

import com.example.domain.model.Leaderboard
import com.example.domain.model.LeaderboardCategory
import com.example.domain.model.LeaderboardEntry
import com.example.domain.model.LeaderboardRank

/**
 * Turns a country's entries into a numbered board.
 *
 * Standard competition ranking: players on the same score share the same position, and the next
 * score down skips the places they took between them (1, 2, 2, 4). Two players who walked exactly
 * as far being told one of them is fourth and the other fifth would be the board making up a
 * difference that isn't there.
 */
object LeaderboardRanking {

    fun rank(
        entries: List<LeaderboardEntry>,
        category: LeaderboardCategory,
        countryCode: String,
        currentPlayerId: String?
    ): Leaderboard {
        // One entry per player, and only the country asked about: a stale duplicate from a rename
        // would otherwise show up as a second player with the same numbers.
        val ordered = entries
            .filter { it.countryCode.equals(countryCode, ignoreCase = true) }
            .associateBy { it.playerId }
            .values
            // Ties are broken by name so the order is the same on every recomposition - the
            // positions are already equal, this only decides which of them is drawn first.
            .sortedWith(
                compareByDescending<LeaderboardEntry> { category.scoreOf(it) }
                    .thenBy { it.nickname.lowercase() }
                    .thenBy { it.playerId }
            )

        var previousScore: Int? = null
        var previousPosition = 0

        val rows = ordered.mapIndexed { index, entry ->
            val score = category.scoreOf(entry)
            val position = if (score == previousScore) previousPosition else index + 1
            previousScore = score
            previousPosition = position

            LeaderboardRank(
                position = position,
                entry = entry,
                score = score,
                isCurrentPlayer = currentPlayerId != null && entry.playerId == currentPlayerId
            )
        }

        return Leaderboard(category = category, countryCode = countryCode.uppercase(), rows = rows)
    }
}
