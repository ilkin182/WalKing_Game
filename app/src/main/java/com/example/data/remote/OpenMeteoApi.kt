package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo's forecast endpoint.
 *
 * Chosen because it needs no API key and no account: the app ships no secret, nothing to rotate,
 * and nothing to break when a free tier changes hands. Non-commercial use is free and unauthenticated
 * (https://open-meteo.com/en/docs).
 *
 * `timezone=auto` is what makes the response usable without timezone arithmetic - every time in it,
 * including sunrise and sunset, comes back on the queried location's own wall clock.
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): ForecastResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        const val CURRENT_FIELDS =
            "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code," +
                "wind_speed_10m,wind_direction_10m"

        const val DAILY_FIELDS = "sunrise,sunset"
    }
}

@JsonClass(generateAdapter = true)
data class ForecastResponse(
    @Json(name = "current") val current: CurrentWeatherDto? = null,
    @Json(name = "daily") val daily: DailyDto? = null
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    /** Local wall-clock time of the observation, e.g. `2026-08-01T19:00`. */
    @Json(name = "time") val time: String? = null,
    @Json(name = "temperature_2m") val temperatureCelsius: Double? = null,
    @Json(name = "apparent_temperature") val apparentTemperatureCelsius: Double? = null,
    @Json(name = "relative_humidity_2m") val humidityPercent: Int? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "wind_speed_10m") val windSpeedKmh: Double? = null,
    @Json(name = "wind_direction_10m") val windDirectionDegrees: Int? = null
)

/** Daily values arrive as parallel arrays, one entry per forecast day. */
@JsonClass(generateAdapter = true)
data class DailyDto(
    @Json(name = "sunrise") val sunrise: List<String>? = null,
    @Json(name = "sunset") val sunset: List<String>? = null
)
