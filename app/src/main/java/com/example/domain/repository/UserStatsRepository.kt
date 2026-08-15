package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserStatsRepository {
    val nickname: Flow<String>
    val totalDistanceWalked: Flow<Double>
    val statsStartTimestamp: Flow<Long>

    /**
     * The ISO country code the player signed up under, or null if they never picked one - which is
     * every account created before the sign-up form asked. Part of who the player is rather than
     * part of their progress, so [resetStats] leaves it alone, exactly like the nickname.
     */
    val countryCode: Flow<String?>

    /**
     * How many closed loops the player has walked, counted as they happen.
     *
     * A running total rather than something re-derived from the cells, because the event vanishes
     * into the map: the moment a loop's interior is claimed it looks exactly like ground that was
     * walked over, and nothing afterwards can tell the two apart.
     */
    val closedLoops: Flow<Int>

    fun updateNickname(name: String)
    fun updateCountry(code: String)
    fun addDistance(deltaMeters: Double)
    fun recordClosedLoops(count: Int)
    fun resetStats()
}
