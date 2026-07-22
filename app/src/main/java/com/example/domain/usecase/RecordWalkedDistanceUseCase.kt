package com.example.domain.usecase

import com.example.domain.model.GeoLocation
import com.example.domain.repository.UserStatsRepository

/**
 * Accumulates walked distance between successive GPS fixes, filtering out GPS jump
 * anomalies and drift while stationary. Holds the last accepted location as session state.
 */
class RecordWalkedDistanceUseCase(private val repository: UserStatsRepository) {
    private var lastRecordedLocation: GeoLocation? = null

    operator fun invoke(newLocation: GeoLocation) {
        val lastLoc = lastRecordedLocation
        if (lastLoc != null) {
            val distance = haversineMeters(lastLoc, newLocation)
            if (distance in 2.0..250.0 && newLocation.accuracyMeters < 25f && lastLoc.accuracyMeters < 25f) {
                repository.addDistance(distance)
            }
        }
        if (newLocation.accuracyMeters < 25f) {
            lastRecordedLocation = newLocation
        }
    }

    fun reset() {
        lastRecordedLocation = null
    }

    private fun haversineMeters(from: GeoLocation, to: GeoLocation): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(from.latitude)) * Math.cos(Math.toRadians(to.latitude)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
