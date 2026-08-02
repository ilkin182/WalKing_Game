package com.example.domain.repository

import com.example.domain.model.Weather

interface WeatherRepository {
    /**
     * The current weather at the given point, or null when it could not be fetched (offline, the
     * service is down, a malformed response). Weather is decoration on the profile screen, so a
     * failure is a missing card rather than an error the player has to dismiss.
     */
    suspend fun currentWeather(lat: Double, lng: Double): Weather?

    /**
     * The last reading already in hand, without going near the network.
     *
     * Used on the stomping path, which runs every few seconds while walking and must never block on
     * a request. Null means nothing has been fetched yet, in which case the cell is simply recorded
     * without weather.
     */
    fun lastKnown(): Weather?
}
