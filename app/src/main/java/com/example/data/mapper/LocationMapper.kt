package com.example.data.mapper

import android.location.Location
import com.example.domain.model.GeoLocation

fun Location.toDomain(): GeoLocation = GeoLocation(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    timestampMillis = time,
    // hasSpeed() is false on fixes that were not derived from doppler/motion at all, where `speed`
    // is simply 0f - which reads as "standing still" and is exactly the wrong thing to tell the
    // travel-mode filter. Passing null instead lets it fall back to the distance between fixes.
    speedMetersPerSecond = if (hasSpeed()) speed else null
)
