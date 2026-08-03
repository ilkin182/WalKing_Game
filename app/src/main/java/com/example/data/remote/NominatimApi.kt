package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenStreetMap's Nominatim - where a named town is and how far it extends.
 *
 * Overpass answers what is on the ground; this answers where the ground ends. The two city
 * achievements - fifty cells in the centre, and reaching the edge - are the only things that need
 * it, so it is asked once per town the player has ever walked in and the answer is cached in Room
 * next to the tiles.
 *
 * Nominatim's usage policy caps this at one request a second and requires an identifying
 * User-Agent, which [NetworkModule] attaches. Neither is a constraint in practice: a player visits a
 * handful of towns in a lifetime of walking, and each is looked up once.
 */
interface NominatimApi {

    @GET("search")
    suspend fun search(
        @Query("q") place: String,
        @Query("countrycodes") countryCodes: String? = null,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 1
    ): List<NominatimPlaceDto>

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/"
    }
}

/**
 * One search result.
 *
 * The coordinates come back as strings, which is Nominatim's own format rather than an oversight -
 * they are decimal degrees printed to whatever precision the object warrants. [boundingBox] is
 * `[south, north, west, east]`, in that order, and is what the city achievements measure against.
 */
@JsonClass(generateAdapter = true)
data class NominatimPlaceDto(
    @Json(name = "lat") val lat: String? = null,
    @Json(name = "lon") val lon: String? = null,
    @Json(name = "boundingbox") val boundingBox: List<String>? = null,
    @Json(name = "name") val name: String? = null
)
