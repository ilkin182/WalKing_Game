package com.example.domain.usecase

import com.example.domain.model.PlaceInfo
import com.example.domain.repository.GeocodingRepository

/**
 * Where the player is, by name: neighbourhood, town and country.
 *
 * One lookup feeds two things - the zone label on the map and the place recorded with every cell
 * claimed - so it is resolved once per fix and passed to both.
 */
class ResolvePlaceUseCase(private val repository: GeocodingRepository) {
    suspend operator fun invoke(lat: Double, lng: Double): PlaceInfo? =
        runCatching { repository.reverseGeocode(lat, lng) }.getOrNull()
}
