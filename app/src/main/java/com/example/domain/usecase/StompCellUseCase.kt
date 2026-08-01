package com.example.domain.usecase

import com.example.domain.engine.GeoPath
import com.example.domain.engine.HexGridConfig
import com.example.domain.engine.HexGridEngine
import com.example.domain.model.Coordinate
import com.example.domain.repository.StompedHexRepository

/**
 * Resolves a lat/lng into a hex cell, stomps it, and triggers enclosed-area fill.
 *
 * When the previous fix is supplied as [from], every cell the straight segment between the two
 * fixes crosses is stomped as well. GPS reports a position only every few seconds, so a walking -
 * let alone a driving - player covers more than one cell between fixes; stomping just the fix
 * positions would leave unclaimed holes along the trail.
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
        forceRestomp: Boolean = false,
        from: Coordinate? = null
    ): String? {
        val cellAddress = hexGridEngine.latLngToCellAddress(lat, lng, HexGridConfig.RESOLUTION)

        val trail = (trailCells(from, Coordinate(lat, lng)) + cellAddress).distinct()
        val cellsToStomp = if (forceRestomp) trail else trail.filterNot(alreadyStompedAddresses::contains)
        if (cellsToStomp.isEmpty()) return null

        repository.stompAll(cellsToStomp, neighborhood)
        fillEnclosedAreas(cellAddress, neighborhood, alreadyStompedAddresses + cellsToStomp)
        return cellAddress
    }

    /**
     * The cells crossed on the way from [from] to [to], in travel order and excluding the
     * destination cell itself (the caller always adds that one).
     */
    private fun trailCells(from: Coordinate?, to: Coordinate): List<String> {
        if (from == null) return emptyList()

        // Further than a player can plausibly move between two fixes means the GPS jumped rather
        // than the player walked, so there is no trail to claim - drawing one would hand them a
        // corridor of cells they never set foot in.
        if (GeoPath.distanceMeters(from, to) > MAX_TRAIL_SEGMENT_METERS) return emptyList()

        return GeoPath.sample(from, to, HexGridConfig.PATH_SAMPLE_STEP_METERS)
            .map { hexGridEngine.latLngToCellAddress(it.lat, it.lng, HexGridConfig.RESOLUTION) }
    }

    private companion object {
        const val MAX_TRAIL_SEGMENT_METERS = 250.0
    }
}
