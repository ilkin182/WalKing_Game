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

    /**
     * @param countsAsWalked whether the ground covered since the last fix was walked. False while the
     * player is riding: the odometer still follows them, so getting out of a car measures from where
     * they got out rather than from where they got in, but the kilometres of the ride are not theirs
     * to keep. Crediting them would put a drive across town on a distance the game calls walked.
     */
    operator fun invoke(newLocation: GeoLocation, countsAsWalked: Boolean = true): Double {
        var accepted = 0.0
        val lastLoc = lastRecordedLocation
        if (lastLoc != null && countsAsWalked) {
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
