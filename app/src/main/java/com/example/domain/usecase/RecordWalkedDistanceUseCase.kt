package com.example.domain.usecase

import com.example.domain.model.GeoLocation
import com.example.domain.repository.UserStatsRepository

/**
 * Accumulates walked distance between successive GPS fixes, filtering out GPS jump
 * anomalies and drift while stationary. Holds the last accepted location as session state.
 *
 * Returns the metres it accepted, or 0.0 when the fix was rejected, so the caller can put the same
 * figure towards the walk in progress without repeating the filtering.
 */
class RecordWalkedDistanceUseCase(private val repository: UserStatsRepository) {
    private var lastRecordedLocation: GeoLocation? = null

    operator fun invoke(newLocation: GeoLocation): Double {
        var accepted = 0.0
        val lastLoc = lastRecordedLocation
        if (lastLoc != null) {
            val distance = haversineMeters(lastLoc, newLocation)
            if (distance in 2.0..250.0 && newLocation.accuracyMeters < 25f && lastLoc.accuracyMeters < 25f) {
                repository.addDistance(distance)
                accepted = distance
            }
        }
        if (newLocation.accuracyMeters < 25f) {
            lastRecordedLocation = newLocation
        }
        return accepted
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
