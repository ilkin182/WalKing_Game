package com.example.domain.usecase

import com.example.domain.model.RegionStat
import com.example.domain.repository.StompedHexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class ObserveRegionStatsUseCase(private val repository: StompedHexRepository) {
    operator fun invoke(): Flow<List<RegionStat>> =
        repository.stompedHexes.map { hexes ->
            hexes.mapNotNull { it.neighborhood }
                .filter { it != ACTIVE_ZONE_NAME }
                .groupingBy { it }
                .eachCount()
                .map { (name, count) ->
                    val total = Math.abs(name.hashCode() % 8000) + 2000 // Mock total between 2000 and 10000
                    RegionStat(name, count, total)
                }
                .sortedByDescending { it.percentage }
        }.catch { emit(emptyList()) } // degrade instead of crashing on a Room query failure

    private companion object {
        const val ACTIVE_ZONE_NAME = "Active Zone"
    }
}
