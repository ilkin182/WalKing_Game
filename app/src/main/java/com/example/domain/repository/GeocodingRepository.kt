package com.example.domain.repository

import com.example.domain.model.PlaceInfo

interface GeocodingRepository {
    /**
     * The place at a point: neighbourhood, town and country, as far as the device can resolve them.
     *
     * Returns null when nothing could be resolved at all. Individual fields are independently
     * nullable - a rural point often has a country and no neighbourhood.
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): PlaceInfo?
}
