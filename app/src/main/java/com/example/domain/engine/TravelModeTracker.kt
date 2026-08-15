package com.example.domain.engine

import com.example.domain.model.Coordinate
import com.example.domain.model.GeoLocation

/**
 * Tells walking apart from riding, so only ground actually covered on foot gets claimed.
 *
 * Feed it every accepted fix; it answers whether the player is currently on foot. A car, a bus or
 * a metro train would otherwise paint a corridor of cells across the whole city in a few minutes -
 * the same corridor a walker takes a week to earn.
 *
 * The decision is deliberately sticky in both directions. It takes [ExplorationRules.VEHICLE_SPEED_SAMPLES]
 * consecutive fast fixes to conclude the player is riding, so one GPS jump cannot cost them their
 * walk; and once riding, it takes a sustained [ExplorationRules.ON_FOOT_CONFIRMATION_MS] at
 * pedestrian pace to conclude they got out, so every red light does not hand back a fistful of cells.
 *
 * Not thread-safe; call it from one place (the ViewModel's location handler).
 */
class TravelModeTracker(
    private val vehicleSpeedMps: Float = ExplorationRules.VEHICLE_SPEED_MPS,
    private val onFootSpeedMps: Float = ExplorationRules.ON_FOOT_SPEED_MPS,
    private val onFootConfirmationMs: Long = ExplorationRules.ON_FOOT_CONFIRMATION_MS,
    private val vehicleSpeedSamples: Int = ExplorationRules.VEHICLE_SPEED_SAMPLES
) {
    private var lastFix: GeoLocation? = null
    private var fastFixes = 0
    private var slowSinceMs: Long? = null

    /** Whether the last fix was judged to be a vehicle ride, for the UI to explain itself with. */
    var isInVehicle: Boolean = false
        private set

    /**
     * @return true when [location] was reached on foot and may claim ground, false while the
     * player is riding. A fix that says nothing about speed (no provider reading, no usable
     * previous fix) leaves the current mode as it is rather than guessing.
     */
    fun isOnFoot(location: GeoLocation): Boolean {
        val speed = speedAt(location)
        lastFix = location
        if (speed == null) return !isInVehicle

        if (speed >= vehicleSpeedMps) {
            fastFixes++
            if (fastFixes >= vehicleSpeedSamples) {
                isInVehicle = true
                slowSinceMs = null
            }
            // Already-fast fixes stop claiming ground right away, even before the count is reached:
            // the confirmation exists to avoid a *lasting* misjudgement, not to wave through the
            // cells crossed while it is being made.
            return false
        }

        fastFixes = 0
        if (!isInVehicle) return true

        if (speed > onFootSpeedMps) {
            // Still moving faster than a pedestrian - a car in traffic, not a car that was parked.
            slowSinceMs = null
            return false
        }

        val since = slowSinceMs ?: location.timestampMillis.also { slowSinceMs = it }
        // A fix stamped before the one that started the stretch (a device clock change, a replayed
        // fix) would make the wait look negative and never end; restart the stretch instead.
        if (location.timestampMillis < since) {
            slowSinceMs = location.timestampMillis
            return false
        }
        if (location.timestampMillis - since < onFootConfirmationMs) return false

        isInVehicle = false
        slowSinceMs = null
        return true
    }

    /** Forgets the ride - used when tracking stops, so resuming somewhere else starts clean. */
    fun reset() {
        lastFix = null
        fastFixes = 0
        slowSinceMs = null
        isInVehicle = false
    }

    /**
     * The speed to judge [location] by: what the provider measured, or failing that what the
     * distance from the previous fix implies.
     *
     * Only fixes a sensible interval apart are used for the fallback. Milliseconds apart, the
     * distance is mostly GPS noise divided by almost nothing; minutes apart, the app was likely not
     * even watching, and the straight line between the two says nothing about how it was covered.
     */
    private fun speedAt(location: GeoLocation): Float? {
        location.speedMetersPerSecond?.let { if (it >= 0f) return it }

        val previous = lastFix ?: return null
        val elapsedMs = location.timestampMillis - previous.timestampMillis
        if (elapsedMs < MIN_SAMPLE_INTERVAL_MS || elapsedMs > MAX_SAMPLE_INTERVAL_MS) return null

        val meters = GeoPath.distanceMeters(
            Coordinate(previous.latitude, previous.longitude),
            Coordinate(location.latitude, location.longitude)
        )
        return (meters / (elapsedMs / 1000.0)).toFloat()
    }

    private companion object {
        /** Below this the distance between two fixes is mostly noise, and the implied speed junk. */
        const val MIN_SAMPLE_INTERVAL_MS = 1_000L

        /** Beyond this the app was not really following the player, so the line between is a guess. */
        const val MAX_SAMPLE_INTERVAL_MS = 120_000L
    }
}
