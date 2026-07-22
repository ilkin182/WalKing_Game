package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserStatsRepository {
    val nickname: Flow<String>
    val totalDistanceWalked: Flow<Double>
    val statsStartTimestamp: Flow<Long>

    fun updateNickname(name: String)
    fun addDistance(deltaMeters: Double)
    fun resetStats()
}
