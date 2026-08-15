package com.example.ui.auth

import com.example.domain.repository.UserStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Just enough of the player's stats for [com.example.domain.usecase.SignUpUseCase], which touches
 * only the country it records once the account exists.
 */
class FakeUserStatsRepository : UserStatsRepository {
    private val country = MutableStateFlow<String?>(null)

    var lastStoredCountry: String? = null
        private set

    override val nickname: Flow<String> = MutableStateFlow("Tester")
    override val totalDistanceWalked: Flow<Double> = MutableStateFlow(0.0)
    override val statsStartTimestamp: Flow<Long> = MutableStateFlow(0L)
    override val closedLoops: Flow<Int> = MutableStateFlow(0)
    override val countryCode: Flow<String?> = country

    override fun updateNickname(name: String) {}

    override fun updateCountry(code: String) {
        lastStoredCountry = code
        country.value = code
    }

    override fun addDistance(deltaMeters: Double) {}
    override fun recordClosedLoops(count: Int) {}
    override fun resetStats() {}
}
