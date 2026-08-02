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
     * Whether [location] is precise enough to clear the cell it lands in.
     *
     * A reported accuracy of exactly 0 means "unknown" on some providers and "perfect" on mock ones;
     * it is accepted here, matching how [com.example.domain.usecase.RecordWalkedDistanceUseCase]
     * already treats accuracy, so a mock provider still drives the map during development.
     */
    fun isAccurateEnough(location: GeoLocation): Boolean =
        location.accuracyMeters >= 0f && location.accuracyMeters <= MAX_ACCURACY_METERS
}
