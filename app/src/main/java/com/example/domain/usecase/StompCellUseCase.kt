package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.repository.StompedHexRepository

/**
 * Resolves a lat/lng into a hex cell, stomps it, and triggers enclosed-area fill.
 */
class StompCellUseCase(
    private val repository: StompedHexRepository,
    private val hexGridEngine: HexGridEngine,
    private val fillEnclosedAreas: FillEnclosedAreasUseCase
) {
    suspend operator fun invoke(
        lat: Double,
        lng: Double,
        neighborhood: String?,
        alreadyStompedAddresses: Set<String>,
        forceRestomp: Boolean = false
    ): String? {
        val cellAddress = hexGridEngine.latLngToCellAddress(lat, lng, RESOLUTION)
        if (!forceRestomp && alreadyStompedAddresses.contains(cellAddress)) return null

        repository.stomp(cellAddress, neighborhood)
        fillEnclosedAreas(cellAddress, neighborhood, alreadyStompedAddresses + cellAddress)
        return cellAddress
    }

    private companion object {
        const val RESOLUTION = 11
    }
}
