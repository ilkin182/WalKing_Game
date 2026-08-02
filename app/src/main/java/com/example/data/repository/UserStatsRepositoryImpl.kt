package com.example.data.repository

import android.content.Context
import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserStatsRepositoryImpl(context: Context) : UserStatsRepository {
    private val prefs = context.getSharedPreferences("stomped_stats", Context.MODE_PRIVATE)

    private val _nickname = MutableStateFlow(prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME) ?: DEFAULT_NICKNAME)
    override val nickname: Flow<String> = _nickname.asStateFlow()

    private val _totalDistanceWalked = MutableStateFlow(prefs.getFloat(KEY_DISTANCE, 0f).toDouble())
    override val totalDistanceWalked: Flow<Double> = _totalDistanceWalked.asStateFlow()

    private val _statsStartTimestamp = MutableStateFlow(
        prefs.getLong(KEY_START_TIME, System.currentTimeMillis()).also {
            if (!prefs.contains(KEY_START_TIME)) {
                prefs.edit().putLong(KEY_START_TIME, it).apply()
            }
        }
    )
    override val statsStartTimestamp: Flow<Long> = _statsStartTimestamp.asStateFlow()

    private val _closedLoops = MutableStateFlow(prefs.getInt(KEY_CLOSED_LOOPS, 0))
    override val closedLoops: Flow<Int> = _closedLoops.asStateFlow()

    override fun updateNickname(name: String) {
        _nickname.value = name
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }

    override fun addDistance(deltaMeters: Double) {
        val newTotal = _totalDistanceWalked.value + deltaMeters
        _totalDistanceWalked.value = newTotal
        prefs.edit().putFloat(KEY_DISTANCE, newTotal.toFloat()).apply()
    }

    override fun recordClosedLoops(count: Int) {
        if (count <= 0) return
        val newTotal = _closedLoops.value + count
        _closedLoops.value = newTotal
        prefs.edit().putInt(KEY_CLOSED_LOOPS, newTotal).apply()
    }

    override fun resetStats() {
        _totalDistanceWalked.value = 0.0
        _closedLoops.value = 0
        val now = System.currentTimeMillis()
        _statsStartTimestamp.value = now
        prefs.edit()
            .putFloat(KEY_DISTANCE, 0f)
            .putInt(KEY_CLOSED_LOOPS, 0)
            .putLong(KEY_START_TIME, now)
            .apply()
    }

    private companion object {
        const val KEY_NICKNAME = "player_nickname"
        const val KEY_DISTANCE = "total_distance_meters"
        const val KEY_START_TIME = "stats_start_time"
        const val KEY_CLOSED_LOOPS = "closed_loops"
        const val DEFAULT_NICKNAME = "Stomper Explorer"
    }
}
