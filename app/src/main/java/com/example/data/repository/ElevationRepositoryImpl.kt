package com.example.data.repository

import com.example.data.remote.ElevationApi
import com.example.domain.model.Coordinate
import com.example.domain.repository.ElevationRepository
import java.util.Locale

class ElevationRepositoryImpl(private val api: ElevationApi) : ElevationRepository {

    override suspend fun elevations(points: List<Coordinate>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val batch = points.take(ElevationApi.MAX_POINTS_PER_REQUEST)

        return try {
            val response = api.elevation(
                latitudes = batch.joinToString(",") { format(it.lat) },
                longitudes = batch.joinToString(",") { format(it.lng) }
            )
            // A short answer would silently pair elevations with the wrong cells, so a response that
            // does not line up one-to-one is discarded rather than partially trusted.
            response.elevation?.takeIf { it.size == batch.size }.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Fixed notation and a dot separator - a comma-decimal locale would break the query string. */
    private fun format(degrees: Double) = String.format(Locale.US, "%.6f", degrees)
}
