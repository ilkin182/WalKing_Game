package com.example.domain.repository

import com.example.domain.model.LeaderboardEntry
import kotlinx.coroutines.flow.Flow

/**
 * Where the standings of everyone in a country come from.
 *
 * Kept as a port with exactly two operations - read a country, publish yourself - so the current
 * on-device implementation can be swapped for a real backend (Firestore, an API) without anything
 * above this line changing. See `LocalLeaderboardRepository` for what backs it today.
 */
interface LeaderboardRepository {
    /** Everyone currently on [countryCode]'s board, unordered - ranking is the domain's job. */
    fun observeCountry(countryCode: String): Flow<List<LeaderboardEntry>>

    /** Puts the player's own figures on the board, replacing whatever was there for them. */
    suspend fun publish(entry: LeaderboardEntry)
}
