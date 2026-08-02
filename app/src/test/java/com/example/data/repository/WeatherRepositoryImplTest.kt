package com.example.data.repository

import com.example.data.remote.CurrentWeatherDto
import com.example.data.remote.DailyDto
import com.example.data.remote.ForecastResponse
import com.example.data.remote.OpenMeteoApi
import com.example.domain.model.WeatherCondition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

private class FakeOpenMeteoApi(
    var response: ForecastResponse? = sampleResponse(),
    var failure: Exception? = null
) : OpenMeteoApi {
    var calls = 0
        private set
    var lastLatitude: Double? = null
    var lastLongitude: Double? = null

    override suspend fun forecast(
        latitude: Double,
        longitude: Double,
        current: String,
        daily: String,
        timezone: String,
        forecastDays: Int
    ): ForecastResponse {
        calls++
        lastLatitude = latitude
        lastLongitude = longitude
        failure?.let { throw it }
        return response ?: ForecastResponse()
    }
}

private fun sampleResponse(
    temperature: Double = 24.5,
    code: Int = 0,
    time: String = "2026-08-01T13:00"
) = ForecastResponse(
    current = CurrentWeatherDto(
        time = time,
        temperatureCelsius = temperature,
        apparentTemperatureCelsius = 26.0,
        humidityPercent = 44,
        weatherCode = code,
        windSpeedKmh = 11.2,
        windDirectionDegrees = 275
    ),
    daily = DailyDto(
        sunrise = listOf("2026-08-01T05:52"),
        sunset = listOf("2026-08-01T20:11")
    )
)

class WeatherRepositoryImplTest {

    private var clock = 1_000L

    private fun repository(api: OpenMeteoApi, ttl: Long = 10 * 60 * 1000L) =
        WeatherRepositoryImpl(api, ttlMillis = ttl, now = { clock })

    @Test
    fun `a forecast becomes a reading the card can draw`() = runTest {
        val api = FakeOpenMeteoApi()

        val weather = repository(api).currentWeather(40.4093, 49.8671)!!

        assertEquals(24.5, weather.temperatureCelsius, 1e-6)
        assertEquals(26.0, weather.feelsLikeCelsius, 1e-6)
        assertEquals(44, weather.humidityPercent)
        assertEquals(275, weather.windDirectionDegrees)
        assertEquals(WeatherCondition.CLEAR, weather.condition)
        assertEquals(5 * 60 + 52, weather.sunriseMinuteOfDay)
        assertEquals(20 * 60 + 11, weather.sunsetMinuteOfDay)
        assertEquals(13 * 60, weather.observedMinuteOfDay)
        assertEquals(40.4093, api.lastLatitude!!, 1e-9)
    }

    @Test
    fun `walking a few streets reuses the reading instead of refetching`() = runTest {
        val api = FakeOpenMeteoApi()
        val repository = repository(api)

        repository.currentWeather(40.4093, 49.8671)
        // ~200 m away: the same weather by any measure the card shows.
        repository.currentWeather(40.4110, 49.8680)

        assertEquals(1, api.calls)
    }

    @Test
    fun `moving well away fetches again`() = runTest {
        val api = FakeOpenMeteoApi()
        val repository = repository(api)

        repository.currentWeather(40.4093, 49.8671)
        repository.currentWeather(40.6000, 49.8671)

        assertEquals(2, api.calls)
    }

    @Test
    fun `a stale reading is refetched`() = runTest {
        val api = FakeOpenMeteoApi()
        val repository = repository(api, ttl = 1_000L)

        repository.currentWeather(40.4093, 49.8671)
        clock += 1_001L
        repository.currentWeather(40.4093, 49.8671)

        assertEquals(2, api.calls)
    }

    @Test
    fun `a network failure with nothing cached leaves the card empty rather than crashing`() = runTest {
        val api = FakeOpenMeteoApi(failure = IOException("offline"))

        assertNull(repository(api).currentWeather(40.4093, 49.8671))
    }

    @Test
    fun `going offline keeps showing the last reading`() = runTest {
        val api = FakeOpenMeteoApi()
        val repository = repository(api, ttl = 1_000L)
        val first = repository.currentWeather(40.4093, 49.8671)
        assertNotNull(first)

        clock += 5_000L
        api.failure = IOException("tunnel")
        val afterFailure = repository.currentWeather(40.4093, 49.8671)

        // The temperature from a few minutes ago beats a blank card while the player is underground.
        assertEquals(first, afterFailure)
    }

    @Test
    fun `a response missing the fields the card needs counts as no reading`() = runTest {
        val api = FakeOpenMeteoApi(response = ForecastResponse(current = null, daily = null))

        assertNull(repository(api).currentWeather(40.4093, 49.8671))
    }

    @Test
    fun `a response without sun times counts as no reading`() = runTest {
        val api = FakeOpenMeteoApi(
            response = sampleResponse().copy(daily = DailyDto(sunrise = emptyList(), sunset = null))
        )

        assertNull(repository(api).currentWeather(40.4093, 49.8671))
    }
}
