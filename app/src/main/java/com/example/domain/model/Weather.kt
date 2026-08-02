package com.example.domain.model

/**
 * The weather where the player is standing, as the profile screen needs it.
 *
 * All times are minutes past local midnight *at the observed location* rather than timestamps. The
 * upstream forecast is requested in the location's own timezone, so the sunrise, the sunset and the
 * observation all arrive on the same wall clock - and comparing them needs no timezone arithmetic,
 * which is what keeps [daylightProgress] correct for a player who has crossed a zone boundary.
 *
 * @property temperatureCelsius the measured air temperature.
 * @property feelsLikeCelsius apparent temperature - what the wind and humidity make it feel like.
 * @property humidityPercent relative humidity, 0..100.
 * @property windSpeedKmh wind speed at 10 m.
 * @property windDirectionDegrees the direction the wind blows *from*, clockwise from north.
 * @property condition the sky, reduced from the WMO code the forecast reports.
 * @property sunriseMinuteOfDay local sunrise.
 * @property sunsetMinuteOfDay local sunset.
 * @property observedMinuteOfDay local time the observation is for.
 */
data class Weather(
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val condition: WeatherCondition,
    val sunriseMinuteOfDay: Int,
    val sunsetMinuteOfDay: Int,
    val observedMinuteOfDay: Int,
    /**
     * The raw WMO code behind [condition], kept so it can be stored with a claimed cell without
     * flattening away detail the achievements might later want to tell apart.
     */
    val weatherCode: Int? = null
) {
    /**
     * How far through the daylight the observation is: 0 at sunrise, 1 at sunset, null when the sun
     * is down. Drives the arc on the profile card, so night is "no sun to place" rather than a dot
     * parked at one end.
     */
    val daylightProgress: Float?
        get() {
            val daylight = sunsetMinuteOfDay - sunriseMinuteOfDay
            if (daylight <= 0) return null
            if (observedMinuteOfDay !in sunriseMinuteOfDay..sunsetMinuteOfDay) return null
            return (observedMinuteOfDay - sunriseMinuteOfDay).toFloat() / daylight
        }

    /** True while the sun is up, which is also when [daylightProgress] has a value. */
    val isDaytime: Boolean get() = daylightProgress != null

    /** Length of the day, for the "N saat M dəqiqə" line under the arc. */
    val daylightMinutes: Int get() = (sunsetMinuteOfDay - sunriseMinuteOfDay).coerceAtLeast(0)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /**
         * Minutes past midnight from an ISO local time like `2026-08-01T19:05`, or null if the
         * string is not one. Deliberately hand-rolled: `java.time` needs library desugaring that
         * this module does not enable, and the shape here is fixed by the forecast API.
         */
        fun parseMinuteOfDay(isoLocalDateTime: String?): Int? {
            val time = isoLocalDateTime?.substringAfter('T', missingDelimiterValue = "") ?: return null
            val hours = time.substringBefore(':', missingDelimiterValue = "").toIntOrNull() ?: return null
            val minutes = time.substringAfter(':').take(2).toIntOrNull() ?: return null
            if (hours !in 0..23 || minutes !in 0..59) return null
            return hours * 60 + minutes
        }

        /** `19:05` for a minute-of-day, for display. */
        fun formatMinuteOfDay(minuteOfDay: Int): String {
            val wrapped = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return "%02d:%02d".format(wrapped / 60, wrapped % 60)
        }
    }
}

/**
 * The sky, reduced from WMO code 4677 to the handful of states worth showing an icon for.
 *
 * The forecast reports 28 distinct codes; a player glancing at their profile wants to know whether
 * to expect rain, not whether the drizzle is freezing.
 */
enum class WeatherCondition(val label: String) {
    CLEAR("Açıq"),
    MAINLY_CLEAR("Əsasən açıq"),
    CLOUDY("Buludlu"),
    FOG("Duman"),
    DRIZZLE("Çiskin"),
    RAIN("Yağış"),
    SHOWERS("Leysan"),
    SNOW("Qar"),
    THUNDERSTORM("İldırım"),
    UNKNOWN("Naməlum");

    companion object {
        fun fromWmoCode(code: Int?): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2, 3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67 -> RAIN
            80, 81, 82 -> SHOWERS
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

/**
 * The eight-point compass, for reporting where the wind comes from.
 *
 * A bearing in degrees is precise and unreadable; "şimal-qərb" is what a player can act on.
 */
enum class WindDirection(val label: String, val short: String) {
    NORTH("Şimal", "Şm"),
    NORTH_EAST("Şimal-şərq", "ŞmŞ"),
    EAST("Şərq", "Ş"),
    SOUTH_EAST("Cənub-şərq", "CŞ"),
    SOUTH("Cənub", "C"),
    SOUTH_WEST("Cənub-qərb", "CQ"),
    WEST("Qərb", "Q"),
    NORTH_WEST("Şimal-qərb", "ŞmQ");

    companion object {
        /** Nearest of the eight points to [degrees], measured clockwise from north. */
        fun fromDegrees(degrees: Int): WindDirection {
            val normalised = ((degrees % 360) + 360) % 360
            // +22.5 so each point owns the 45 degrees centred on it, not the 45 after it.
            val sector = ((normalised + 22.5) / 45.0).toInt() % entries.size
            return entries[sector]
        }
    }
}
