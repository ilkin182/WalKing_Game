package com.example.domain.engine

import com.example.domain.model.GeoLocation

/**
 * When a GPS fix is allowed to clear fog.
 *
 * Clearing fog is permanent, so a fix has to earn it. A phone in a street canyon happily reports a
 * position 80 m from where the player is standing; acting on that would carve out cells they never
 * set foot in, and no later fix can put the fog back.
 */
object ExplorationRules {

    /** Fixes vaguer than this are ignored for exploration purposes. */
    const val MAX_ACCURACY_METERS = 50.0f

    /** How long the player has to stay put before their surroundings count as seen. */
    const val DWELL_THRESHOLD_MS = 30_000L

    /**
     * Moving this fast is a vehicle, not a walk: 6.5 m/s is 23 km/h, well past a sprint and slow
     * for a car. Ground covered above it is not claimed - the game is about walking, and a single
     * drive across town would otherwise hand the player more territory than a month of walking.
     */
    const val VEHICLE_SPEED_MPS = 6.5f

    /**
     * Back under this (10.8 km/h) counts as being on foot again. Deliberately above a walking pace
     * and below the vehicle threshold: it is the gap that stops a car in slow traffic from reading
     * as a pedestrian, while still letting a jogger through.
     */
    const val ON_FOOT_SPEED_MPS = 3.0f

    /**
     * How long the player has to keep to [ON_FOOT_SPEED_MPS] before claiming ground resumes.
     *
     * A car stops - at lights, in a jam, in a car park - far more often than it ends a journey, so
     * leaving vehicle mode takes a sustained minute of pedestrian pace rather than a single slow fix.
     */
    const val ON_FOOT_CONFIRMATION_MS = 60_000L

    /**
     * How many consecutive over-the-limit fixes it takes to decide the player is in a vehicle.
     *
     * Two rather than one: a fix that jumps between buildings implies a wild speed for one sample,
     * and treating that as a car ride would cost the player the next minute of walking.
     */
    const val VEHICLE_SPEED_SAMPLES = 2

    /**
     * Whether [location] is precise enough to clear the cell it lands in.
     *
     * A reported accuracy of exactly 0 means "unknown" on some providers and "perfect" on mock ones;
     * it is accepted here, matching how [com.example.domain.usecase.RecordWalkedDistanceUseCase]
     * already treats accuracy, so a mock provider still drives the map during development.
     */
    fun isAccurateEnough(location: GeoLocation): Boolean =
        location.accuracyMeters >= 0f && location.accuracyMeters <= MAX_ACCURACY_METERS
}
