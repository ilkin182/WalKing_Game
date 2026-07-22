package com.example.domain.usecase

import com.example.domain.engine.HexGridEngine
import com.example.domain.model.ActiveNeighborhood
import com.example.domain.model.Coordinate
import com.example.domain.repository.GeocodingRepository

/**
 * Reverse-geocodes the player's position into a neighborhood name and (re)computes the
 * ~1.5km boundary polygon around it. Returns null when the active neighborhood is unchanged.
 */
class UpdateActiveNeighborhoodUseCase(
    private val geocodingRepository: GeocodingRepository,
    private val hexGridEngine: HexGridEngine
) {
    suspend operator fun invoke(lat: Double, lng: Double, current: ActiveNeighborhood?): ActiveNeighborhood? {
        return try {
            val name = geocodingRepository.reverseGeocodePlaceName(lat, lng) ?: DEFAULT_ZONE_NAME
            if (current != null && current.name == name) {
                null
            } else {
                buildNeighborhood(name, lat, lng)
            }
        } catch (e: Exception) {
            // Fallback for network/geocoder failure: keep current active or set default centered on current location
            if (current == null) {
                buildNeighborhood(DEFAULT_ZONE_NAME, lat, lng)
            } else {
                null
            }
        }
    }

    private fun buildNeighborhood(name: String, lat: Double, lng: Double): ActiveNeighborhood {
        // Boundary polygon (750m radius square ~= 1.5km width)
        val halfSideMeters = 750.0
        val deltaLat = halfSideMeters / 111111.0
        val deltaLng = halfSideMeters / (111111.0 * Math.cos(Math.toRadians(lat)))

        val corners = listOf(
            Coordinate(lat + deltaLat, lng - deltaLng),
            Coordinate(lat + deltaLat, lng + deltaLng),
            Coordinate(lat - deltaLat, lng + deltaLng),
            Coordinate(lat - deltaLat, lng - deltaLng)
        )

        val activeCells = try {
            hexGridEngine.polygonToCells(corners, RESOLUTION)
        } catch (e: Exception) {
            emptySet()
        }

        return ActiveNeighborhood(name = name, centerLat = lat, centerLng = lng, totalCells = activeCells)
    }

    private companion object {
        const val RESOLUTION = 11
        const val DEFAULT_ZONE_NAME = "Active Zone"
    }
}
