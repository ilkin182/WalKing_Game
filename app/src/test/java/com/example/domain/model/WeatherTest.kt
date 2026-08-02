package com.example.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    private fun weather(
        sunrise: Int = 6 * 60,
        sunset: Int = 20 * 60,
        observed: Int = 13 * 60
    ) = Weather(
        temperatureCelsius = 21.0,
        feelsLikeCelsius = 20.0,
        humidityPercent = 50,
        windSpeedKmh = 10.0,
        windDirectionDegrees = 0,
        condition = WeatherCondition.CLEAR,
        sunriseMinuteOfDay = sunrise,
        sunsetMinuteOfDay = sunset,
        observedMinuteOfDay = observed
    )

    @Test
    fun `daylight progress runs from sunrise to sunset`() {
        assertEquals(0f, weather(observed = 6 * 60).daylightProgress!!, 1e-6f)
        assertEquals(0.5f, weather(observed = 13 * 60).daylightProgress!!, 1e-6f)
        assertEquals(1f, weather(observed = 20 * 60).daylightProgress!!, 1e-6f)
    }

    @Test
    fun `there is no sun to place at night`() {
        // Before dawn and after dusk the arc should show no sun at all, rather than one parked at
        // whichever end it was clamped to.
        assertNull(weather(observed = 5 * 60).daylightProgress)
        assertNull(weather(observed = 22 * 60).daylightProgress)
        assertFalse(weather(observed = 22 * 60).isDaytime)
        assertTrue(weather(observed = 13 * 60).isDaytime)
    }

    @Test
    fun `a day with no daylight does not divide by zero`() {
        // Polar winter: the forecast can report sunrise and sunset at the same minute.
        val polarNight = weather(sunrise = 12 * 60, sunset = 12 * 60, observed = 12 * 60)

        assertNull(polarNight.daylightProgress)
        assertEquals(0, polarNight.daylightMinutes)
    }

    @Test
    fun `daylight length is the gap between the two`() {
        assertEquals(14 * 60, weather(sunrise = 6 * 60, sunset = 20 * 60).daylightMinutes)
    }

    @Test
    fun `iso local times parse to minutes past midnight`() {
        assertEquals(0, Weather.parseMinuteOfDay("2026-08-01T00:00"))
        assertEquals(19 * 60 + 5, Weather.parseMinuteOfDay("2026-08-01T19:05"))
        assertEquals(23 * 60 + 59, Weather.parseMinuteOfDay("2026-12-31T23:59"))
        // Seconds are tolerated even though the forecast does not send them.
        assertEquals(6 * 60 + 30, Weather.parseMinuteOfDay("2026-08-01T06:30:00"))
    }

    @Test
    fun `anything that is not an iso local time parses to null`() {
        assertNull(Weather.parseMinuteOfDay(null))
        assertNull(Weather.parseMinuteOfDay(""))
        assertNull(Weather.parseMinuteOfDay("2026-08-01"))
        assertNull(Weather.parseMinuteOfDay("2026-08-01T25:00"))
        assertNull(Weather.parseMinuteOfDay("2026-08-01T12:75"))
        assertNull(Weather.parseMinuteOfDay("2026-08-01Tzz:zz"))
    }

    @Test
    fun `minutes format as a zero padded clock`() {
        assertEquals("00:00", Weather.formatMinuteOfDay(0))
        assertEquals("06:05", Weather.formatMinuteOfDay(6 * 60 + 5))
        assertEquals("23:59", Weather.formatMinuteOfDay(23 * 60 + 59))
    }
}

class WeatherConditionTest {

    @Test
    fun `wmo codes collapse to the states worth an icon`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmoCode(0))
        assertEquals(WeatherCondition.MAINLY_CLEAR, WeatherCondition.fromWmoCode(1))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromWmoCode(3))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmoCode(48))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromWmoCode(53))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmoCode(65))
        assertEquals(WeatherCondition.SHOWERS, WeatherCondition.fromWmoCode(81))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmoCode(75))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmoCode(86))
        assertEquals(WeatherCondition.THUNDERSTORM, WeatherCondition.fromWmoCode(99))
    }

    @Test
    fun `an unmapped or missing code is unknown rather than a crash`() {
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(null))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(-1))
        assertEquals(WeatherCondition.UNKNOWN, WeatherCondition.fromWmoCode(1000))
    }

    @Test
    fun `every condition has a label to put under the temperature`() {
        assertTrue(WeatherCondition.entries.all { it.label.isNotBlank() })
    }
}

class WindDirectionTest {

    @Test
    fun `each cardinal bearing maps to its own point`() {
        assertEquals(WindDirection.NORTH, WindDirection.fromDegrees(0))
        assertEquals(WindDirection.NORTH_EAST, WindDirection.fromDegrees(45))
        assertEquals(WindDirection.EAST, WindDirection.fromDegrees(90))
        assertEquals(WindDirection.SOUTH_EAST, WindDirection.fromDegrees(135))
        assertEquals(WindDirection.SOUTH, WindDirection.fromDegrees(180))
        assertEquals(WindDirection.SOUTH_WEST, WindDirection.fromDegrees(225))
        assertEquals(WindDirection.WEST, WindDirection.fromDegrees(270))
        assertEquals(WindDirection.NORTH_WEST, WindDirection.fromDegrees(315))
    }

    @Test
    fun `a point owns the sector centred on it, not the one after it`() {
        // 80 degrees is closer to east than to north-east, and has to read that way.
        assertEquals(WindDirection.EAST, WindDirection.fromDegrees(80))
        assertEquals(WindDirection.NORTH_EAST, WindDirection.fromDegrees(60))
        assertEquals(WindDirection.NORTH, WindDirection.fromDegrees(20))
    }

    @Test
    fun `bearings wrap instead of falling off the end`() {
        assertEquals(WindDirection.NORTH, WindDirection.fromDegrees(360))
        assertEquals(WindDirection.NORTH, WindDirection.fromDegrees(350))
        assertEquals(WindDirection.NORTH, WindDirection.fromDegrees(-10))
        assertEquals(WindDirection.WEST, WindDirection.fromDegrees(-90))
    }
}
