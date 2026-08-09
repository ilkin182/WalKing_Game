package com.example.domain.model

data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    /**
     * Ground speed the provider reported, in metres per second, or null when it reported none.
     *
     * Null is common - a fix built from wifi/cell has no speed at all - so anything reading this
     * has to cope without it; see [com.example.domain.engine.TravelModeTracker], which falls back
     * to the speed implied by the previous fix.
     */
    val speedMetersPerSecond: Float? = null
)
