package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserStatsRepository {
    val nickname: Flow<String>
    val totalDistanceWalked: Flow<Double>
    val statsStartTimestamp: Flow<Long>

    /**
     * How many closed loops the player has walked, counted as they happen.
     *
     * A running total rather than something re-derived from the cells, because the event vanishes
     * into the map: the moment a loop's interior is claimed it looks exactly like ground that was
     * walked over, and nothing afterwards can tell the two apart.
     */
    val closedLoops: Flow<Int>

    fun updateNickname(name: String)
    fun addDistance(deltaMeters: Double)
    fun recordClosedLoops(count: Int)
    fun resetStats()
}
