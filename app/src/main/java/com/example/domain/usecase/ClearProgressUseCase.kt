package com.example.domain.usecase

import com.example.domain.repository.StompedHexRepository
import com.example.domain.repository.UserStatsRepository
import com.example.domain.repository.WalkSessionRepository

/**
 * Wipes everything the player has built up.
 *
 * The walks go with the cells: leaving them behind would reset the map while the achievements went
 * on being measured against walks whose ground no longer exists, which reads as a bug from the
 * player's side. The routes those walks traced are cleared with them.
 *
 * The cached parks, bridges and town boundaries deliberately stay. They are not the player's
 * progress - they are what the world looks like, and it looks the same after a reset. Throwing them
 * away would mean asking Overpass and Nominatim all over again for answers already on disk, which is
 * the one thing [com.example.domain.repository.PoiRepository] exists to avoid; the geography
 * achievements simply start counting from an empty history against the same cache.
 */
class ClearProgressUseCase(
    private val stompedHexRepository: StompedHexRepository,
    private val userStatsRepository: UserStatsRepository,
    private val walkSessionRepository: WalkSessionRepository
) {
    suspend operator fun invoke() {
        stompedHexRepository.clearAll()
        walkSessionRepository.clearAll()
        userStatsRepository.resetStats()
    }
}
