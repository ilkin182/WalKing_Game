package com.example.data.repository

import android.content.Context
import com.example.data.local.LocationTrackingState
import com.example.domain.model.GeoLocation
import com.example.domain.repository.LocationRepository
import com.example.location.LocationTrackingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

class LocationRepositoryImpl(private val context: Context) : LocationRepository {
    // Actual GPS requests happen inside LocationTrackingService (a foreground service), so
    // updates keep flowing while the app is backgrounded or the screen is locked. This repository
    // just relays the shared state the service publishes to.
    override val locationUpdates: Flow<GeoLocation> = LocationTrackingState.location.filterNotNull()
    override val errors: Flow<String> = LocationTrackingState.error.filterNotNull()

    override fun startUpdates(intervalMs: Long) {
        LocationTrackingService.start(context, intervalMs)
    }

    override fun stopUpdates() {
        LocationTrackingService.stop(context)
    }
}
