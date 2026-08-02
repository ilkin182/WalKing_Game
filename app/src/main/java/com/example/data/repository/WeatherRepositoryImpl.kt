package com.example.data.repository

import com.example.data.mapper.toDomain
import com.example.data.remote.OpenMeteoApi
import com.example.domain.model.Weather
import com.example.domain.repository.WeatherRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Fetches the weather, and does so rarely.
 *
 * The player's position updates every few seconds while they walk, and the profile card asks for the
 * weather whenever it is shown - so without a cache this would be a network request per GPS fix. Two
 * things stop that: a reading is reused for [ttlMillis], and it is reused for any position within
 * [CACHE_RADIUS_DEGREES] of where it was taken, because walking a couple of streets does not change
 * the weather. The lock means a burst of simultaneous asks makes one request, not five.
 */
class WeatherRepositoryImpl(
    private val api: OpenMeteoApi,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis
) : WeatherRepository {

    private data class CachedReading(
        val lat: Double,
        val lng: Double,
        val fetchedAt: Long,
        val weather: Weather
    )

    private val mutex = Mutex()

    @Volatile
    private var cached: CachedReading? = null

    override suspend fun currentWeather(lat: Double, lng: Double): Weather? = mutex.withLock {
        cached?.takeIf { it.isFreshFor(lat, lng) }?.let { return it.weather }

        val fetched = try {
            api.forecast(latitude = lat, longitude = lng).toDomain()
        } catch (e: Exception) {
            // Offline, a timeout, a 5xx, a response shape that changed under us - all the same to
            // the profile screen, which simply shows no card. The previous reading is deliberately
            // kept: yesterday's temperature beats a blank while the player walks through a tunnel.
            null
        } ?: return cached?.weather

        cached = CachedReading(lat, lng, now(), fetched)
        return fetched
    }

    // Deliberately unsynchronised: a plain read of the latest reference, so the stomping path never
    // waits on an in-flight fetch holding the lock.
    override fun lastKnown(): Weather? = cached?.weather

    private fun CachedReading.isFreshFor(lat: Double, lng: Double): Boolean =
        now() - fetchedAt < ttlMillis &&
            abs(this.lat - lat) < CACHE_RADIUS_DEGREES &&
            abs(this.lng - lng) < CACHE_RADIUS_DEGREES

    companion object {
        /** Ten minutes: finer than any observation the free forecast actually updates at. */
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1000L

        /** ~1.1 km. Below this the reading is the same weather by any measure a player cares about. */
        const val CACHE_RADIUS_DEGREES = 0.01
    }
}
