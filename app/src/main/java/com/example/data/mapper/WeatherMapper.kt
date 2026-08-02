package com.example.data.mapper

import com.example.data.remote.ForecastResponse
import com.example.domain.model.Weather
import com.example.domain.model.WeatherCondition

/**
 * Forecast response to domain [Weather], or null when the response is missing anything the card
 * cannot be drawn without.
 *
 * Every field in the DTO is nullable because a forecast service is free to omit one, and a partly
 * filled card ("--°, wind --") is worse than no card. Temperature, the sky and both sun times are
 * required; the rest fall back to a neutral value rather than dropping the whole reading.
 */
fun ForecastResponse.toDomain(): Weather? {
    val current = current ?: return null

    val temperature = current.temperatureCelsius ?: return null
    val observed = Weather.parseMinuteOfDay(current.time) ?: return null
    val sunrise = Weather.parseMinuteOfDay(daily?.sunrise?.firstOrNull()) ?: return null
    val sunset = Weather.parseMinuteOfDay(daily?.sunset?.firstOrNull()) ?: return null

    return Weather(
        temperatureCelsius = temperature,
        feelsLikeCelsius = current.apparentTemperatureCelsius ?: temperature,
        humidityPercent = (current.humidityPercent ?: 0).coerceIn(0, 100),
        windSpeedKmh = (current.windSpeedKmh ?: 0.0).coerceAtLeast(0.0),
        windDirectionDegrees = current.windDirectionDegrees ?: 0,
        condition = WeatherCondition.fromWmoCode(current.weatherCode),
        weatherCode = current.weatherCode,
        sunriseMinuteOfDay = sunrise,
        sunsetMinuteOfDay = sunset,
        observedMinuteOfDay = observed
    )
}
