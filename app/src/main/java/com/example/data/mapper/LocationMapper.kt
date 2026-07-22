package com.example.data.mapper

import android.location.Location
import com.example.domain.model.GeoLocation

fun Location.toDomain(): GeoLocation = GeoLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    timestampMillis = time
)
