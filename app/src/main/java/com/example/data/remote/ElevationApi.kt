package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo's elevation lookup - the same keyless service as the forecast.
 *
 * Coordinates go up as comma-separated lists and come back as one array in the same order, so a
 * hundred cells cost one request. That batching is the whole reason elevation is filled in by a
 * background pass rather than at the moment a cell is claimed: per-cell it would be a network round
 * trip every few seconds while walking.
 */
interface ElevationApi {

    @GET("v1/elevation")
    suspend fun elevation(
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String
    ): ElevationResponse

    companion object {
        /** Open-Meteo's documented ceiling for one elevation request. */
        const val MAX_POINTS_PER_REQUEST = 100
    }
}

@JsonClass(generateAdapter = true)
data class ElevationResponse(
    @Json(name = "elevation") val elevation: List<Double>? = null
)
