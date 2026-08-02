package com.example.domain.usecase

import com.example.domain.model.Weather
import com.example.domain.repository.WeatherRepository

/** The weather where the player is standing. Null when it is not available right now. */
class GetWeatherUseCase(private val repository: WeatherRepository) {
    suspend operator fun invoke(lat: Double, lng: Double): Weather? =
        repository.currentWeather(lat, lng)
}

/**
 * The last weather reading already in hand, without touching the network.
 *
 * Used when recording a claimed cell, which happens every few seconds while walking - see
 * [WeatherRepository.lastKnown].
 */
class GetWeatherSnapshotUseCase(private val repository: WeatherRepository) {
    operator fun invoke(): Weather? = repository.lastKnown()
}
