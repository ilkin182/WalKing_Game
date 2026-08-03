package com.example.data.mapper

import com.example.data.local.entity.CityBoundsEntity
import com.example.data.local.entity.PoiEntity
import com.example.domain.model.CityBounds
import com.example.domain.model.Coordinate
import com.example.domain.model.GeoBounds
import com.example.domain.model.PoiKind
import com.example.domain.model.PointOfInterest

/**
 * A cached row as a place, or null when the row's kind is one this build does not know.
 *
 * Kinds are stored by name, so an install that has cached rows written by a newer version - or by a
 * version whose kinds were later renamed - skips them rather than crashing on `valueOf`.
 */
fun PoiEntity.toDomain(): PointOfInterest? {
    val parsed = PoiKind.entries.firstOrNull { it.name == kind } ?: return null
    return PointOfInterest(
        id = id,
        kind = parsed,
        name = name,
        center = Coordinate(lat, lng),
        bounds = toBounds()
    )
}

fun PointOfInterest.toEntity(tileKey: String): PoiEntity = PoiEntity(
    id = id,
    tileKey = tileKey,
    kind = kind.name,
    name = name,
    lat = center.lat,
    lng = center.lng,
    north = bounds?.north,
    south = bounds?.south,
    east = bounds?.east,
    west = bounds?.west
)

/** A box only when all four sides are there; a half-written box would be worse than none. */
private fun PoiEntity.toBounds(): GeoBounds? {
    val n = north ?: return null
    val s = south ?: return null
    val e = east ?: return null
    val w = west ?: return null
    return GeoBounds(north = n, south = s, east = e, west = w)
}

fun CityBoundsEntity.toDomain(): CityBounds = CityBounds(
    city = city,
    countryCode = countryCode,
    bounds = if (found && north != null && south != null && east != null && west != null) {
        GeoBounds(north = north, south = south, east = east, west = west)
    } else {
        null
    },
    center = if (found && centerLat != null && centerLng != null) {
        Coordinate(centerLat, centerLng)
    } else {
        null
    }
)

fun CityBounds.toEntity(fetchedAt: Long): CityBoundsEntity = CityBoundsEntity(
    city = city,
    countryCode = countryCode,
    found = found,
    north = bounds?.north,
    south = bounds?.south,
    east = bounds?.east,
    west = bounds?.west,
    centerLat = center?.lat,
    centerLng = center?.lng,
    fetchedAt = fetchedAt
)
