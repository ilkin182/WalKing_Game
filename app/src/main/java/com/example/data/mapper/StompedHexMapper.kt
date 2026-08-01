package com.example.data.mapper

import com.example.data.local.entity.StompedHexEntity
import com.example.domain.model.CellContext
import com.example.domain.model.StompedHex

fun StompedHexEntity.toDomain(): StompedHex = StompedHex(
    hexAddress = hexAddress,
    neighborhood = neighborhood,
    timestamp = timestamp,
    explorationLevel = explorationLevel,
    context = toContext()
)

fun StompedHex.toEntity(): StompedHexEntity = StompedHexEntity(
    hexAddress = hexAddress,
    neighborhood = neighborhood,
    timestamp = timestamp,
    explorationLevel = explorationLevel,
    temperatureC = context?.temperatureCelsius,
    weatherCode = context?.weatherCode,
    windKmh = context?.windSpeedKmh,
    sunriseMinute = context?.sunriseMinuteOfDay,
    sunsetMinute = context?.sunsetMinuteOfDay,
    city = context?.city,
    countryCode = context?.countryCode,
    elevationM = context?.elevationMeters
)

/**
 * The stored conditions as a [CellContext], or null when the row carries none - which is every row
 * written before schema version 6, and any row written while offline.
 */
private fun StompedHexEntity.toContext(): CellContext? {
    val context = CellContext(
        temperatureCelsius = temperatureC,
        weatherCode = weatherCode,
        windSpeedKmh = windKmh,
        sunriseMinuteOfDay = sunriseMinute,
        sunsetMinuteOfDay = sunsetMinute,
        city = city,
        countryCode = countryCode,
        elevationMeters = elevationM
    )
    return if (context.isEmpty) null else context
}
