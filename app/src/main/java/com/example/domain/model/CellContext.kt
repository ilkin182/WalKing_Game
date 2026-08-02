package com.example.domain.model

/**
 * What the world was like where and when a cell was claimed.
 *
 * Recorded alongside the cell because none of it can be reconstructed afterwards: nobody can look up
 * what the weather was at a particular corner at a particular minute last March. The place and the
 * elevation *can* be filled in later, and are nullable for that reason too - cells claimed before
 * this existed, or while offline, simply carry nothing.
 *
 * Every field is optional on purpose. A missing value means "not known", never "zero", and the
 * achievement rules treat it that way.
 *
 * @property temperatureCelsius air temperature at the moment of claiming.
 * @property weatherCode the WMO code, so the sky can be re-derived without storing a label.
 * @property windSpeedKmh wind speed at the moment of claiming.
 * @property sunriseMinuteOfDay local sunrise that day, for the dawn/dusk achievements.
 * @property sunsetMinuteOfDay local sunset that day.
 * @property city the town or city, from reverse geocoding.
 * @property countryCode ISO country code, from reverse geocoding.
 * @property elevationMeters height above sea level; filled in by a later batch pass.
 */
data class CellContext(
    val temperatureCelsius: Double? = null,
    val weatherCode: Int? = null,
    val windSpeedKmh: Double? = null,
    val sunriseMinuteOfDay: Int? = null,
    val sunsetMinuteOfDay: Int? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val elevationMeters: Double? = null
) {
    /** True when nothing at all was captured, so it need not be stored. */
    val isEmpty: Boolean
        get() = temperatureCelsius == null && weatherCode == null && windSpeedKmh == null &&
            sunriseMinuteOfDay == null && sunsetMinuteOfDay == null &&
            city == null && countryCode == null && elevationMeters == null

    companion object {
        val EMPTY = CellContext()

        /** The context as it was recorded, plus an elevation resolved later. */
        fun CellContext?.withElevation(meters: Double?): CellContext =
            (this ?: EMPTY).copy(elevationMeters = meters)
    }
}

/**
 * A place, as far as reverse geocoding can tell.
 *
 * [neighborhood] is what the map's zone label uses; [city] and [countryCode] are what the travel
 * achievements need, and were previously thrown away by the geocoder.
 */
data class PlaceInfo(
    val neighborhood: String? = null,
    val city: String? = null,
    val countryCode: String? = null
)
