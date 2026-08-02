package com.example.domain.repository

import com.example.domain.model.Coordinate

interface ElevationRepository {
    /**
     * Height above sea level for each point, in the order given. Returns an empty list when the
     * lookup failed - elevation is enrichment, so a failure means "try again later", never an error
     * the player sees.
     */
    suspend fun elevations(points: List<Coordinate>): List<Double>
}
