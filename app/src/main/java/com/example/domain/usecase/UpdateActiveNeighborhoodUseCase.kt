package com.example.domain.usecase

import com.example.domain.engine.HexGridConfig
import com.example.domain.engine.HexGridEngine
import com.example.domain.model.ActiveNeighborhood
import com.example.domain.model.Coordinate
import com.example.domain.model.PlaceInfo

/**
 * Turns an already-resolved place into the active neighborhood and its ~1.5km boundary polygon.
 * Returns null when the active neighborhood is unchanged.
 *
 * The reverse geocoding itself moved out to [ResolvePlaceUseCase]: the same lookup now also feeds
 * the city and country recorded with each claimed cell, and doing it here would mean geocoding the
 * player's position twice on every fix.
 */
class UpdateActiveNeighborhoodUseCase(
    private val hexGridEngine: HexGridEngine
) {
    operator fun invoke(
        lat: Double,
        lng: Double,
        current: ActiveNeighborhood?,
        place: PlaceInfo?
    ): ActiveNeighborhood? {
        val name = place?.neighborhood ?: DEFAULT_ZONE_NAME
        return when {
            current == null -> buildNeighborhood(name, lat, lng)
            current.name == name -> null
            else -> buildNeighborhood(name, lat, lng)
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
            hexGridEngine.polygonToCells(corners, HexGridConfig.RESOLUTION)
        } catch (e: Exception) {
            emptySet()
        }

        return ActiveNeighborhood(name = name, centerLat = lat, centerLng = lng, totalCells = activeCells)
    }

    private companion object {
        const val DEFAULT_ZONE_NAME = "Active Zone"
    }
}
