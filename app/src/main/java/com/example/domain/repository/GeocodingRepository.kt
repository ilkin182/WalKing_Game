package com.example.domain.repository

interface GeocodingRepository {
    suspend fun reverseGeocodePlaceName(lat: Double, lng: Double): String?
}
