package com.example.domain.repository

import com.example.domain.model.GeoLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    val locationUpdates: Flow<GeoLocation>
    val errors: Flow<String>

    fun startUpdates(intervalMs: Long)
    fun stopUpdates()
}
