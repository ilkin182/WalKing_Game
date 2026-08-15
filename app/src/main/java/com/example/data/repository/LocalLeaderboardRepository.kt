package com.example.data.repository

import com.example.data.leaderboard.SampleRivals
import com.example.domain.model.LeaderboardEntry
import com.example.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The leaderboard while the app has no server, in the same shape one with a server would have.
 *
 * The player's own row is real - it is whatever the game last published from their actual explored
 * cells and unlocked badges. The players around them are [SampleRivals], a fixed field per country,
 * because nothing on this device can know about anybody else's phone. That means the *position* is
 * a position against a benchmark field rather than against live players, which is why the screen
 * says so rather than letting it pass for a global ranking.
 *
 * Nothing is persisted: the player's row is republished from live stats every time the board is
 * opened, so there is no stale copy worth keeping between launches.
 *
 * Swapping in a real backend is one line in AppContainer - the interface it implements
 * ([LeaderboardRepository]) is already the one a networked implementation needs.
 */
class LocalLeaderboardRepository : LeaderboardRepository {

    private val published = MutableStateFlow<LeaderboardEntry?>(null)

    override fun observeCountry(countryCode: String): Flow<List<LeaderboardEntry>> =
        published.map { player ->
            val field = SampleRivals.forCountry(countryCode)
            if (player != null && player.countryCode.equals(countryCode, ignoreCase = true)) {
                field + player
            } else {
                field
            }
        }

    override suspend fun publish(entry: LeaderboardEntry) {
        published.value = entry
    }
}
